package service;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.UUID;

class BookingPersistenceTest extends BookingScenarios {
    @Override AnnotationConfigApplicationContext openContext() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() +
                ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000;INIT=SET TIME ZONE 'UTC'");
        source.setUser("sa");
        return BookingTestContext.open(source, BookingTestContext.localLocks());
    }
}
