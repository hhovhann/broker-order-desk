package dev.brokerdesk.repo;

import dev.brokerdesk.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {}
