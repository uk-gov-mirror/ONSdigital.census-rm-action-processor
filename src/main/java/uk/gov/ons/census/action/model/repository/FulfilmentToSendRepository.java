package uk.gov.ons.census.action.model.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uk.gov.ons.census.action.model.entity.FulfilmentToSend;

public interface FulfilmentToSendRepository extends JpaRepository<FulfilmentToSend, Long> {

  @Query("SELECT DISTINCT f.fulfilmentCode FROM FulfilmentToSend f")
  List<String> findDistinctFulfilmentCode();
}
