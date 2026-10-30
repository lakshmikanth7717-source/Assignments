package entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Project LocalDateTime values denote UTC. Use an explicit instant before Hibernate's
 * UTC JDBC binding so the JVM default zone cannot shift a deadline in DATETIME columns.
 */
@Converter(autoApply = true)
public class UtcDateTimeConverter implements AttributeConverter<LocalDateTime, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(LocalDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant(ZoneOffset.UTC));
    }
    @Override
    public LocalDateTime convertToEntityAttribute(Timestamp value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }
}
