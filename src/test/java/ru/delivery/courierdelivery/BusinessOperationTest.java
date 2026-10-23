package ru.delivery.courierdelivery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.delivery.courierdelivery.repository.RouteRepository;
import ru.delivery.courierdelivery.repository.ShipmentAssignmentRepository;
import ru.delivery.courierdelivery.repository.ShipmentRepository;
import ru.delivery.courierdelivery.repository.StopRepository;
import ru.delivery.courierdelivery.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessOperationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private RouteRepository routeRepository;
    @Autowired
    private StopRepository stopRepository;
    @Autowired
    private ShipmentAssignmentRepository assignmentRepository;

    @BeforeEach
    void clean() {
        assignmentRepository.deleteAll();
        shipmentRepository.deleteAll();
        stopRepository.deleteAll();
        routeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fullBusinessFlowAndGuards() throws Exception {
        long userId = id(postJson("/api/users", """
                {"name":"Иван","email":"ivan-biz@example.com","role":"SENDER"}
                """));
        long lightId = id(postJson("/api/shipments", """
                {"senderId":%d,"address":"A","weight":2,"status":"CREATED"}
                """.formatted(userId)));
        long heavyId = id(postJson("/api/shipments", """
                {"senderId":%d,"address":"B","weight":20,"status":"CREATED"}
                """.formatted(userId)));
        long routeId = id(postJson("/api/routes", """
                {"routeDate":"2026-10-22","maxWeight":10,"status":"PLANNED"}
                """));
        long stopId = id(postJson("/api/stops", """
                {"routeId":%d,"address":"A","sequenceNumber":1}
                """.formatted(routeId)));

        mockMvc.perform(post("/api/operations/start-route/" + routeId))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/operations/assign-shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentId":%d,"stopId":%d}
                                """.formatted(heavyId, stopId)))
                .andExpect(status().isBadRequest());

        long assignmentId = ((Number) objectMapper.readTree(
                mockMvc.perform(post("/api/operations/assign-shipment")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"shipmentId":%d,"stopId":%d}
                                        """.formatted(lightId, stopId)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.result.shipmentStatus").value("ASSIGNED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray()
        ).get("result").get("assignmentId").numberValue()).longValue();

        mockMvc.perform(post("/api/operations/start-route/" + routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.routeStatus").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/operations/complete-delivery/" + assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shipmentStatus").value("DELIVERED"));

        mockMvc.perform(post("/api/operations/close-route/" + routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.routeStatus").value("COMPLETED"));

        mockMvc.perform(post("/api/operations/cancel-shipment/" + lightId))
                .andExpect(status().isConflict());
    }

    private MvcResult postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private long id(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return node.get("id").asLong();
    }
}
