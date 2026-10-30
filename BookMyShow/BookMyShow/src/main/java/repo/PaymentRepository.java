package repo;

import entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Payment findByIdempotencyKey(String idempotencyKey);
    Payment findByGatewayTransactionId(String gatewayTransactionId);
}
