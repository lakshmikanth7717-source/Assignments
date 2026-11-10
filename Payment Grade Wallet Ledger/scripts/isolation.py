"""Two real PostgreSQL sessions plus a lock observer; isolated disposable lab schema."""
import threading
import time
from pathlib import Path
import psycopg

DSN = 'host=postgres dbname=wallet user=wallet password=local-demo-only'
lines = []


def log(text):
    print(text, flush=True)
    lines.append(text)


with psycopg.connect(DSN, autocommit=True) as observer:
    observer.execute('CREATE SCHEMA IF NOT EXISTS isolation_lab')
    observer.execute('CREATE TABLE IF NOT EXISTS isolation_lab.wallets '
                     '(wallet_id bigint PRIMARY KEY, balance bigint NOT NULL)')
    observer.execute('TRUNCATE isolation_lab.wallets')
    observer.execute('INSERT INTO isolation_lab.wallets VALUES (1, 10000), (2, 0)')
    log('Lab only: balance cache table; no financial history is changed.')
    with psycopg.connect(DSN, autocommit=True) as a, psycopg.connect(DSN, autocommit=True) as b:
        # Show an unsafe check-then-update: both authorize against 10000.
        a.execute('BEGIN ISOLATION LEVEL READ COMMITTED')
        b.execute('BEGIN ISOLATION LEVEL READ COMMITTED')
        for name, conn in [('A', a), ('B', b)]:
            balance = conn.execute('SELECT balance FROM isolation_lab.wallets WHERE wallet_id=1').fetchone()[0]
            log(f'{name}> SELECT balance ...; -> {balance}; approves 8000')
        for name, conn in [('A', a), ('B', b)]:
            conn.execute('UPDATE isolation_lab.wallets SET balance=balance-8000 WHERE wallet_id=1')
            conn.execute('UPDATE isolation_lab.wallets SET balance=balance+8000 WHERE wallet_id=2')
            conn.execute('COMMIT')
            log(f'{name}> debit 8000; credit 8000; COMMIT;')
        unsafe = observer.execute('SELECT * FROM isolation_lab.wallets ORDER BY wallet_id').fetchall()
        assert unsafe == [(1, -6000), (2, 16000)]
        log(f'Unsafe result: {unsafe}')
        observer.execute('UPDATE isolation_lab.wallets SET balance=CASE wallet_id WHEN 1 THEN 10000 ELSE 0 END')
        a.execute('BEGIN ISOLATION LEVEL READ COMMITTED')
        rows = a.execute('SELECT wallet_id,balance FROM isolation_lab.wallets ORDER BY wallet_id FOR NO KEY UPDATE').fetchall()
        log(f'A> BEGIN; SELECT ... ORDER BY wallet_id FOR NO KEY UPDATE; -> {rows}')
        b.execute('BEGIN ISOLATION LEVEL READ COMMITTED')
        b.execute("SET LOCAL statement_timeout = '10s'")
        pid = b.info.backend_pid
        result, errors = [], []

        def wait_for_wallets():
            try:
                result.extend(b.execute('SELECT wallet_id,balance FROM isolation_lab.wallets '
                                       'ORDER BY wallet_id FOR NO KEY UPDATE').fetchall())
            except BaseException as error:
                errors.append(error)

        log('B> BEGIN; SELECT ... ORDER BY wallet_id FOR NO KEY UPDATE; -- waits')
        thread = threading.Thread(target=wait_for_wallets)
        thread.start()
        try:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                waiting = observer.execute("SELECT wait_event_type FROM pg_stat_activity WHERE pid=%s", (pid,)).fetchone()
                if waiting == ('Lock',):
                    break
                time.sleep(0.02)
            else:
                raise AssertionError('B did not block on a PostgreSQL lock')
            log('Observer> pg_stat_activity.wait_event_type for B -> Lock')
            a.execute('UPDATE isolation_lab.wallets SET balance=balance-8000 WHERE wallet_id=1')
            a.execute('UPDATE isolation_lab.wallets SET balance=balance+8000 WHERE wallet_id=2')
            a.execute('COMMIT')
            log('A> debit 8000; credit 8000; COMMIT;')
        finally:
            a.execute('ROLLBACK')
            thread.join(timeout=12)
        assert not thread.is_alive() and not errors, errors
        assert result == [(1, 2000), (2, 8000)]
        log(f'B> lock acquired, returned rows: {result}')
        log('B> 2000 < 8000: INSUFFICIENT_FUNDS; ROLLBACK;')
        b.execute('ROLLBACK')
        final = observer.execute('SELECT * FROM isolation_lab.wallets ORDER BY wallet_id').fetchall()
        assert final == [(1, 2000), (2, 8000)]
        log(f'Safe result: {final}; total=10000; no overdraft. PASS')
    observer.execute('DROP TABLE isolation_lab.wallets')
    observer.execute('DROP SCHEMA isolation_lab')
Path('docs/evidence').mkdir(parents=True, exist_ok=True)
Path('docs/evidence/isolation.txt').write_text('\n'.join(lines) + '\n')
