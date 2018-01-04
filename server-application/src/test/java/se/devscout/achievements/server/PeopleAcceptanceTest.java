package se.devscout.achievements.server;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import se.devscout.achievements.server.api.PersonDTO;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class PeopleAcceptanceTest {
    @ClassRule
    public static final DropwizardAppRule<AchievementsApplicationConfiguration> RULE =
            new DropwizardAppRule<>(
                    MockAchievementsApplication.class,
                    ResourceHelpers.resourceFilePath("server-test-configuration.yaml"));
    private static String organizationId;


    @BeforeClass
    public static void setUp() throws Exception {
        Client client = RULE.client();

        final Response response = TestUtil.request(client, String.format("http://localhost:%d/tasks/bootstrap-data", RULE.getAdminPort()))
                .post(null);
        final String bootstrapResponse = response.readEntity(String.class);
        final Matcher matcher = Pattern.compile("Created organization.* \\(id (.+)\\)").matcher(bootstrapResponse);
        matcher.find();
        organizationId = matcher.group(1);
    }

    @Test
    public void createUpdateGet_happyPath() {
        Client client = RULE.client();

        final PersonDTO dto = new PersonDTO(null, "Alice");

        Response createResponse = TestUtil.request(client, String.format("http://localhost:%d/api/organizations/%s/people", RULE.getLocalPort(), organizationId))
                .post(Entity.json(dto));

        assertThat(createResponse.getStatus()).isEqualTo(HttpStatus.CREATED_201);

        final URI location = createResponse.getLocation();

        dto.name = "Alicia";
        dto.custom_identifier = "alicia";

        Response updateResponse = TestUtil.request(client, location)
                .put(Entity.json(dto));

        assertThat(updateResponse.getStatus()).isEqualTo(HttpStatus.OK_200);

        final PersonDTO response2Dto = updateResponse.readEntity(PersonDTO.class);

        assertThat(response2Dto.name).isEqualTo("Alicia");
        assertThat(response2Dto.custom_identifier).isEqualTo("alicia");
        assertThat(response2Dto.organization.name).isEqualTo("Monsters, Inc.");

        Response getResponse = TestUtil.request(client, location)
                .get();

        assertThat(getResponse.getStatus()).isEqualTo(HttpStatus.OK_200);

        final PersonDTO response3Dto = getResponse.readEntity(PersonDTO.class);

        assertThat(response3Dto.name).isEqualTo("Alicia");
    }

    @Test
    public void create_invalidEmailAddress_expect400() {
        Client client = RULE.client();

        final PersonDTO dto = new PersonDTO(null, "Alice", "alice@invalid", null, null, null, null);

        Response createResponse = TestUtil.request(client, String.format("http://localhost:%d/api/organizations/%s/people", RULE.getLocalPort(), organizationId))
                .post(Entity.json(dto));

        assertThat(createResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);
        assertThat(createResponse.getHeaderString("Content-Type")).contains("application/json");

        final ObjectNode actualResponseEntity = createResponse.readEntity(ObjectNode.class);
        assertThat(actualResponseEntity.has("message")).isTrue();
        assertThat(actualResponseEntity.get("status").asInt()).isEqualTo(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void create_missingName_expect400() {
        Client client = RULE.client();

        final PersonDTO dto = new PersonDTO(null, null, "alice@example.com", null, null, null, null);

        Response createResponse = TestUtil.request(client, String.format("http://localhost:%d/api/organizations/%s/people", RULE.getLocalPort(), organizationId))
                .post(Entity.json(dto));

        assertThat(createResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);
        assertThat(createResponse.getHeaderString("Content-Type")).contains("application/json");

        final ObjectNode actualResponseEntity = createResponse.readEntity(ObjectNode.class);
        assertThat(actualResponseEntity.has("message")).isTrue();
        assertThat(actualResponseEntity.get("status").asInt()).isEqualTo(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void update_missingPerson_expectNotFoundResponse() {
        Client client = RULE.client();

        final PersonDTO dto = new PersonDTO(null, "Alice");

        Response updateResponse = TestUtil.request(client, String.format("http://localhost:%d/api/organizations/%s/people/%d", RULE.getLocalPort(), organizationId, -1))
                .put(Entity.json(dto));

        assertThat(updateResponse.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
    }

}
