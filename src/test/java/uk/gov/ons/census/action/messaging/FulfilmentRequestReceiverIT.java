package uk.gov.ons.census.action.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.action.model.dto.*;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.FulfilmentToSend;
import uk.gov.ons.census.action.model.repository.CaseRepository;
import uk.gov.ons.census.action.model.repository.FulfilmentToSendRepository;

@ContextConfiguration
@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
public class FulfilmentRequestReceiverIT {
  private static final String EVENTS_FULFILMENT_REQUEST_BINDING = "event.fulfilment.request";
  private static final String PRINT_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND = "P_OR_I1";

  @Value("${queueconfig.action-fulfilment-inbound-queue}")
  private String actionFulfilmentQueue;

  @Value("${queueconfig.events-exchange}")
  private String eventsExchange;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;

  @Autowired private CaseRepository caseRepository;

  @Autowired private FulfilmentToSendRepository fulfilmentToSendRepository;

  private EasyRandom easyRandom = new EasyRandom();

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(actionFulfilmentQueue);
    fulfilmentToSendRepository.deleteAll();
  }

  @Test
  public void testQuestionnaireFulfilment() throws InterruptedException {

    // Given
    Case fulfillmentCase = this.setUpCaseAndSaveInDB();
    ResponseManagementEvent actionFulfilmentEvent =
        getResponseManagementEvent(fulfillmentCase.getCaseId(), "P_OR_H1");

    // When
    rabbitQueueHelper.sendMessage(
        eventsExchange, EVENTS_FULFILMENT_REQUEST_BINDING, actionFulfilmentEvent);
    Thread.sleep(2000);

    List<FulfilmentToSend> fulfilmentToSend = fulfilmentToSendRepository.findAll();

    FulfilmentToSend actualFulfilmentToSend = fulfilmentToSend.get(0);

    checkAddressFieldsMatch(
        fulfillmentCase,
        actionFulfilmentEvent.getPayload().getFulfilmentRequest().getContact(),
        actualFulfilmentToSend);
  }

  @Test
  public void testIndividualResponseFulfilmentRequestIsIgnored() {
    Case fulfillmentCase = this.setUpCaseAndSaveInDB();
    UUID parentCaseId = UUID.randomUUID();
    UUID childCaseId = fulfillmentCase.getCaseId();
    ResponseManagementEvent actionFulfilmentEvent =
        getResponseManagementEvent(parentCaseId, PRINT_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);
    actionFulfilmentEvent.getPayload().getFulfilmentRequest().setIndividualCaseId(childCaseId);

    rabbitQueueHelper.sendMessage(
        eventsExchange, EVENTS_FULFILMENT_REQUEST_BINDING, actionFulfilmentEvent);

    assertThat(fulfilmentToSendRepository.findAll().size()).isEqualTo(0);
  }

  private void checkAddressFieldsMatch(
      Case expectedCase, Contact expectedContact, FulfilmentToSend actualFulfilmentToSend) {
    assertThat(actualFulfilmentToSend)
        .isEqualToComparingOnlyGivenFields(
            expectedCase, "addressLine1", "addressLine2", "addressLine3", "postcode", "townName");
    assertThat(actualFulfilmentToSend)
        .isEqualToComparingOnlyGivenFields(expectedContact, "title", "forename", "surname");
    assertThat(actualFulfilmentToSend.getCaze()).isEqualTo(expectedCase);
  }

  private ResponseManagementEvent getResponseManagementEvent(UUID caseId, String fulfilmentCode) {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();

    FulfilmentRequestDTO fulfilmentRequest = easyRandom.nextObject(FulfilmentRequestDTO.class);
    fulfilmentRequest.setFulfilmentCode(fulfilmentCode);
    responseManagementEvent.setPayload(new Payload());
    fulfilmentRequest.setCaseId(caseId);
    responseManagementEvent.getPayload().setFulfilmentRequest(fulfilmentRequest);

    return responseManagementEvent;
  }

  private Case setUpCaseAndSaveInDB() {
    Case fulfilmentCase = easyRandom.nextObject(Case.class);
    caseRepository.saveAndFlush(fulfilmentCase);
    return fulfilmentCase;
  }
}
