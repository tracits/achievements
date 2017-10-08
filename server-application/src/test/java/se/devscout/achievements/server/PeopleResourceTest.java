package se.devscout.achievements.server;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import se.devscout.achievements.server.api.PersonDTO;
import se.devscout.achievements.server.data.dao.ObjectNotFoundException;
import se.devscout.achievements.server.data.dao.OrganizationsDao;
import se.devscout.achievements.server.data.dao.PeopleDao;
import se.devscout.achievements.server.data.model.Organization;
import se.devscout.achievements.server.data.model.Person;
import se.devscout.achievements.server.data.model.PersonProperties;
import se.devscout.achievements.server.resources.PeopleResource;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class PeopleResourceTest {

    private final PeopleDao dao = mock(PeopleDao.class);
    private final OrganizationsDao organizationsDao = mock(OrganizationsDao.class);

    @Rule
    public final ResourceTestRule resources = ResourceTestRule.builder()
            .addResource(new PeopleResource(dao, organizationsDao))
            .build();

    @Test
    public void get_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "Alice");

        final Response response = resources
                .target("/organizations/" + org.getId() + "/people/" + person.getId())
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        final PersonDTO dto = response.readEntity(PersonDTO.class);

        verify(dao).read(eq(person.getId()));
    }

    @Test
    public void getByOrganization_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "Alice");
        when(dao.getByParent(eq(org))).thenReturn(Collections.singletonList(person));

        final Response response = resources
                .target("/organizations/" + org.getId() + "/people")
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        final List<PersonDTO> dto = response.readEntity(new GenericType<List<PersonDTO>>() {
        });
        assertThat(dto).hasSize(1);

        verify(dao).getByParent(eq(org));
    }

    @Test
    public void getByOrganization_missing_expectNotFound() throws Exception {
        final UUID badId = UUID.randomUUID();
        when(organizationsDao.read(eq(badId))).thenThrow(new NotFoundException());

        final Response response = resources
                .target("/organizations/" + badId.toString() + "/people")
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(dao, never()).getByParent(any(Organization.class));
    }

    @Test
    public void get_notFound() throws Exception {
        when(dao.read(eq(123))).thenThrow(new NotFoundException());
        final Response response = resources
                .target("/organizations/" + UUID.randomUUID().toString() + "/people/123")
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(dao).read(eq(123));
    }

    @Test
    public void delete_notFound() throws Exception {
        final Organization org = mockOrganization("Org");

        doThrow(new NotFoundException()).when(dao).read(eq(-1));

        final Response response = resources
                .target("/organizations/ORG_ID/people/PERSON_ID")
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(dao, never()).delete(anyInt());
    }

    @Test
    public void delete_happyPath() throws Exception {
        final Organization org = mockOrganization("Org");
        final Person person = mockPerson(org, "name");

        final Response response = resources
                .target("/organizations/" + org.getId() + "/people/" + person.getId())
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT_204);

        verify(dao).delete(eq(person.getId()));
    }

    @Test
    public void delete_wrongOrganization_expectNotFound() throws Exception {

        final Organization orgA = mockOrganization("ORG_A");
        final Organization orgB = mockOrganization("ORG_B");
        final Person person = mockPerson(orgA, "name");

        final Response response = resources
                .target("/organizations/" + orgB.getId() + "/people/" + person.getId())
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(organizationsDao).read(eq(orgB.getId()));
        verify(dao, never()).delete(anyInt());
    }

    @Test
    public void get_wrongOrganization_expectNotFound() throws Exception {

        final Organization orgA = mockOrganization("ORG_A");
        final Organization orgB = mockOrganization("ORG_B");
        final Person person = mockPerson(orgA, "name");

        final Response response = resources
                .target("/organizations/" + orgB.getId() + "/people/" + person.getId())
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(organizationsDao).read(eq(orgB.getId()));
        verify(dao).read(eq(person.getId()));
    }

    private Person mockPerson(Organization org, String name) throws ObjectNotFoundException {
        final Integer uuid = new Random().nextInt();

        final Person person = mock(Person.class);
        when(person.getId()).thenReturn(uuid);
        when(person.getOrganization()).thenReturn(org);
        when(person.getName()).thenReturn(name);

        when(dao.read(eq(uuid))).thenReturn(person);

        return person;
    }

    private Organization mockOrganization(String name) throws ObjectNotFoundException {
        final UUID uuid = UUID.randomUUID();

        final Organization orgA = mock(Organization.class);
        when(orgA.getId()).thenReturn(uuid);
        when(orgA.getName()).thenReturn(name);

        when(organizationsDao.read(eq(uuid))).thenReturn(orgA);

        return orgA;
    }

    @Test
    public void create_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "name");
        when(dao.create(any(Organization.class), any(PersonProperties.class))).thenReturn(person);

        final Response response = resources
                .target("/organizations/" + org.getId().toString() + "/people")
                .request()
                .post(Entity.json(new PersonDTO()));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED_201);
        final PersonDTO dto = response.readEntity(PersonDTO.class);

        assertThat(response.getLocation().getPath()).isEqualTo("/organizations/" + org.getId() + "/people/" + person.getId());
        assertThat(dto.name).isEqualTo("name");

        verify(dao).create(any(Organization.class), any(PersonProperties.class));
        verify(organizationsDao).read(eq(org.getId()));
    }

}