package se.devscout.achievements.server.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import se.devscout.achievements.server.MockUtil;
import se.devscout.achievements.server.TestUtil;
import se.devscout.achievements.server.api.*;
import se.devscout.achievements.server.auth.Roles;
import se.devscout.achievements.server.auth.password.PasswordValidator;
import se.devscout.achievements.server.auth.password.SecretGenerator;
import se.devscout.achievements.server.data.dao.*;
import se.devscout.achievements.server.data.model.*;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static se.devscout.achievements.server.MockUtil.*;

public class PeopleResourceTest {

    private static final int ZERO = 0;
    private final PeopleDao dao = mock(PeopleDao.class);

    private final OrganizationsDao organizationsDao = mock(OrganizationsDao.class);

    private final AchievementsDao achievementsDao = mock(AchievementsDao.class);

    private final CredentialsDao credentialsDao = mock(CredentialsDao.class);

    private final GroupsDao groupsDao = mock(GroupsDao.class);

    private final GroupMembershipsDao membershipsDao = mock(GroupMembershipsDao.class);

    @Rule
    public final ResourceTestRule resources = TestUtil.resourceTestRule(credentialsDao)
            .addResource(new PeopleResource(dao, organizationsDao, achievementsDao, new ObjectMapper(), groupsDao, membershipsDao))
            .build();

    @Before
    public void setUp() throws Exception {
        MockUtil.setupDefaultCredentials(credentialsDao);
    }


    @Test
    public void get_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "Alice");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + person.getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        final PersonDTO dto = response.readEntity(PersonDTO.class);
        assertThat(dto.id).isNotNull();
        assertThat(dto.id).isNotEqualTo(ZERO);

        verify(dao).read(eq(person.getId()));
    }

    @Test
    public void get_authReader_expectUnauthorized() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "Alice");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + person.getId())
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN_403);

        verify(dao, never()).getByParent(any(Organization.class));
    }

    private Organization mockOrganization(String name) throws ObjectNotFoundException {
        final Organization org = MockUtil.mockOrganization(name);
        when(organizationsDao.read(eq(org.getId()))).thenReturn(org);
        return org;
    }

    @Test
    public void getByOrganization_authReader_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "Alice");
        when(dao.getByParent(eq(org))).thenReturn(Collections.singletonList(person));

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        // A list of PersonBaseDTO should be returned but the point is that we don't accidentally want to return a PersonDTO with custom_identifier value.
        final List<PersonDTO> dto = response.readEntity(new GenericType<List<PersonDTO>>() {
        });
        assertThat(dto).hasSize(1);
        assertThat(dto.get(0).id).isNotNull();
        assertThat(dto.get(0).id).isNotEqualTo(ZERO);
        assertThat(dto.get(0).custom_identifier).isNullOrEmpty();

        verify(dao).getByParent(eq(org));
    }

    @Test
    public void getByOrganization_missing_expectNotFound() throws Exception {
        final UUID badId = UUID.randomUUID();
        when(organizationsDao.read(eq(badId))).thenThrow(new NotFoundException());

        final Response response = resources
                .target("/organizations/" + UuidString.toString(badId) + "/people")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(dao, never()).getByParent(any(Organization.class));
    }

    @Test
    public void getByOrganization_filterByGroup_happyPath() throws Exception {
        final Organization org = mockOrganization("org");

        final Group group1 = mockGroup(org, "Developers");
        final Group group2 = mockGroup(org, "Marketing");

        final Person person1 = mockPerson(org, "Alice");
        when(person1.getMemberships()).thenReturn(Collections.singleton(new GroupMembership(group1, person1, GroupRole.MEMBER)));

        final Person person2 = mockPerson(org, "Bob");
        when(person2.getMemberships()).thenReturn(Collections.singleton(new GroupMembership(group1, person2, GroupRole.MANAGER)));

        final Person person3 = mockPerson(org, "Carol");
        when(person3.getMemberships()).thenReturn(Collections.singleton(new GroupMembership(group2, person3, GroupRole.MEMBER)));

        when(dao.getByParent(eq(org))).thenReturn(Arrays.asList(person1, person2, person3));

        final Response response1 = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .queryParam("group", group1.getId().toString())
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get();

        assertThat(response1.getStatus()).isEqualTo(HttpStatus.OK_200);

        final List<PersonBaseDTO> dto1 = response1.readEntity(new GenericType<List<PersonBaseDTO>>() {
        });
        assertThat(dto1.stream().map(personBaseDTO -> personBaseDTO.name).collect(Collectors.toList())).containsExactlyInAnyOrder("Alice", "Bob");

        final Response response2 = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .queryParam("filter", "a")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get();

        assertThat(response2.getStatus()).isEqualTo(HttpStatus.OK_200);

        final List<PersonBaseDTO> dto2 = response2.readEntity(new GenericType<List<PersonBaseDTO>>() {
        });
        assertThat(dto2.stream().map(personBaseDTO -> personBaseDTO.name).collect(Collectors.toList())).containsExactlyInAnyOrder("Alice", "Carol");

        final Response response3 = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get();

        assertThat(response3.getStatus()).isEqualTo(HttpStatus.OK_200);

        final List<PersonBaseDTO> dto3 = response3.readEntity(new GenericType<List<PersonBaseDTO>>() {
        });
        assertThat(dto3.stream().map(personBaseDTO -> personBaseDTO.name).collect(Collectors.toList())).containsExactlyInAnyOrder("Alice", "Bob", "Carol");

    }

    @Test
    public void get_notFound() throws Exception {
        when(dao.read(eq(123))).thenThrow(new NotFoundException());
        final Response response = resources
                .target("/organizations/" + UuidString.toString(UUID.randomUUID()) + "/people/123")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(dao).read(eq(123));
    }

    @Test
    public void get_byCustomId_notFound() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "Alice", "alice");
        when(dao.read(eq(org), eq("alice"))).thenReturn(person);
        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/c:alice")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        final PersonDTO dto = response.readEntity(PersonDTO.class);
        assertThat(dto.name).isEqualTo("Alice");
        assertThat(dto.custom_identifier).isEqualTo("alice");

        verify(dao, never()).read(anyInt());
        verify(dao).read(eq(org), eq("alice"));
    }

    @Test
    public void delete_notFound() throws Exception {
        doThrow(new NotFoundException()).when(dao).read(eq(-1));

        final Response response = resources
                .target("/organizations/ORG_ID/people/PERSON_ID")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
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
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + person.getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT_204);

        verify(dao).delete(eq(person.getId()));
    }

    @Test
    public void delete_unauthorizedUser_expectForbidden() throws Exception {
        final Organization org = mockOrganization("Org");
        final Person person = mockPerson(org, "name");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + person.getId())
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN_403);

        verify(dao, never()).delete(eq(person.getId()));
    }

    @Test
    public void delete_wrongOrganization_expectNotFound() throws Exception {

        final Organization orgA = mockOrganization("ORG_A");
        final Organization orgB = mockOrganization("ORG_B");
        final Person person = mockPerson(orgA, "name");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(orgB.getId()) + "/people/" + person.getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(organizationsDao).read(eq(orgB.getId()));
        verify(dao, never()).delete(anyInt());
    }

    @Test
    public void delete_self_expectBadRequest() throws Exception {
        final Organization org = mockOrganization("org");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + credentialsDao.get(CredentialsType.PASSWORD, USERNAME_EDITOR).getPerson().getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .delete();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);

        verify(dao, never()).delete(anyInt());
    }

    @Test
    public void get_wrongOrganization_expectNotFound() throws Exception {

        final Organization orgA = mockOrganization("ORG_A");
        final Organization orgB = mockOrganization("ORG_B");
        final Person person = mockPerson(orgA, "name");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(orgB.getId()) + "/people/" + person.getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);

        verify(organizationsDao).read(eq(orgB.getId()));
        verify(dao).read(eq(person.getId()));
    }

    @Test
    public void create_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "name");
        when(dao.create(any(Organization.class), any(PersonProperties.class))).thenReturn(person);

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .post(Entity.json(new PersonDTO()));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED_201);
        final PersonDTO dto = response.readEntity(PersonDTO.class);

        assertThat(response.getLocation().getPath()).isEqualTo("/organizations/" + UuidString.toString(org.getId()) + "/people/" + person.getId());
        assertThat(dto.name).isEqualTo("name");

        verify(dao).create(any(Organization.class), any(PersonProperties.class));
        verify(organizationsDao).read(eq(org.getId()));
    }

    @Test
    public void create_unauthorizedUser_expectForbidden() throws Exception {
        final Organization org = mockOrganization("org");
        final Person person = mockPerson(org, "name");
        when(dao.create(any(Organization.class), any(PersonProperties.class))).thenReturn(person);

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .post(Entity.json(new PersonDTO()));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN_403);

        verify(dao, never()).create(any(Organization.class), any(PersonProperties.class));
    }

    @Test
    public void create_privilegeEscalation_expectBadRequest() throws Exception {
        final Organization org = mockOrganization("org");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .post(Entity.json(new PersonDTO(null, "Alice", "admin")));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);

        verify(dao, never()).create(any(Organization.class), any(PersonProperties.class));
    }

    @Test
    public void update_changeName_happyPath() throws Exception {
        final Organization org = mockOrganization("org");
        final Person expectedPerson = mockPerson(org, "Alicia");
        when(dao.read(eq(expectedPerson.getId()))).thenReturn(expectedPerson);
        when(dao.update(eq(expectedPerson.getId()), any(PersonProperties.class))).thenReturn(expectedPerson);

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + expectedPerson.getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.json(new PersonDTO(null, "Alicia")));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
        final PersonDTO dto = response.readEntity(PersonDTO.class);

        assertThat(dto.name).isEqualTo("Alicia");

        verify(dao).update(eq(expectedPerson.getId()), any(PersonProperties.class));
    }

    @Test
    public void update_privilegeEscalation_expectBadRequest() throws Exception {
        final Organization org = mockOrganization("org");
        final Person expectedPerson = mockPerson(org, "Alicia");
        when(dao.read(eq(expectedPerson.getId()))).thenReturn(expectedPerson);
        when(dao.update(eq(expectedPerson.getId()), any(PersonProperties.class))).thenReturn(expectedPerson);

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + expectedPerson.getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.json(new PersonDTO(null, "Alicia", "admin")));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);

        verify(dao, never()).update(eq(expectedPerson.getId()), any(PersonProperties.class));
    }

    @Test
    public void update_self_expectBadRequest() throws Exception {
        final Organization org = mockOrganization("org");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + credentialsDao.get(CredentialsType.PASSWORD, USERNAME_EDITOR).getPerson().getId())
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.json(new PersonDTO(null, "Alice")));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);

        verify(dao, never()).update(anyInt(), any(PersonProperties.class));
    }

    @Test
    public void batchUpdate_json_happyPath() throws Exception {
        final Organization org = mockOrganization("org");

        final Person alice = mockPerson(org, "Alice");
        when(dao.read(eq(org), eq("aaa"))).thenReturn(alice);
        final ArgumentCaptor<PersonProperties> updateCaptor = ArgumentCaptor.forClass(PersonProperties.class);
        when(dao.update(eq(alice.getId()), updateCaptor.capture())).thenReturn(alice);

        final Person bob = mockPerson(org, "Bob");
        when(dao.read(eq(org), eq("bbb"))).thenReturn(bob);

        final Person carol = mockPerson(org, "Carol");
        when(dao.read(eq(org), eq("ccc"))).thenThrow(new ObjectNotFoundException());
        final ArgumentCaptor<PersonProperties> createCaptor = ArgumentCaptor.forClass(PersonProperties.class);
        when(dao.create(any(Organization.class), createCaptor.capture())).thenReturn(carol);

        final Group group = mockGroup(org, "Developers");
        when(groupsDao.create(eq(org), any())).thenReturn(group);
        when(groupsDao.read(eq(org), eq("Developers")))
                .thenThrow(new ObjectNotFoundException())
                .thenReturn(group);

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.json(Arrays.asList(
                        new PersonDTO(-1, "Alicia", "alice@example.com", "aaa", null, null, null, Lists.newArrayList(new GroupBaseDTO(null, "Developers"))),
                        new PersonDTO(-1, "Carol", "carol@example.com", "ccc", null, null, Collections.singletonList(new PersonAttributeDTO("title", "Boss")), Lists.newArrayList(new GroupBaseDTO(null, "Developers")))
                )));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        final List<PersonBaseDTO> dto = response.readEntity(new GenericType<List<PersonBaseDTO>>() {
        });
        assertThat(dto).hasSize(2);
        assertThat(dto.get(0).id).isNotNull();
        assertThat(dto.get(0).name).isNotNull();
        assertThat(dto.get(1).id).isNotNull();
        assertThat(dto.get(1).name).isNotNull();

        verify(dao).read(eq(org), eq("ccc"));
        verify(dao).create(eq(org), any(PersonProperties.class));
        assertThat(createCaptor.getValue().getCustomIdentifier()).isEqualTo("ccc");
        assertThat(createCaptor.getValue().getName()).isEqualTo("Carol");
        assertThat(createCaptor.getValue().getEmail()).isEqualTo("carol@example.com");
        assertThat(createCaptor.getValue().getAttributes()).contains(new PersonAttribute("title", "Boss"));

        verify(dao).read(eq(org), eq("aaa"));
        verify(dao).update(eq(alice.getId()), any(PersonProperties.class));
        assertThat(updateCaptor.getValue().getCustomIdentifier()).isEqualTo("aaa");
        assertThat(updateCaptor.getValue().getName()).isEqualTo("Alicia");
        assertThat(updateCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(updateCaptor.getValue().getAttributes()).isNull();

        verify(organizationsDao).read(eq(org.getId()));
        verify(groupsDao, times(2)).read(eq(org), eq("Developers"));
        verify(groupsDao, times(1)).create(eq(org), any());
        verify(membershipsDao).add(eq(alice), eq(group), any());
        verify(membershipsDao).add(eq(carol), eq(group), any());
    }

    @Test
    public void batchUpdateJson_unauthorizedUser_expectForbidden() throws Exception {
        final Organization org = mockOrganization("org");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .put(Entity.json(Arrays.asList(
                        new PersonDTO(-1, "Alicia", "alice@example.com", "aaa", null, null, null, null),
                        new PersonDTO(-1, "Carol", "carol@example.com", "ccc", null, null, null, null)
                )));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN_403);

        verify(dao, never()).create(any(Organization.class), any(PersonProperties.class));
        verify(dao, never()).update(anyInt(), any(PersonProperties.class));
    }

    @Test
    public void batchUpdateCsv_unauthorizedUser_expectForbidden() throws Exception {
        final Organization org = mockOrganization("org");

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .put(Entity.entity("" +
                                "name,email,custom_identifier\n" +
                                "Alicia,alice@example.com,aaa\n" +
                                "Carol,carol@example.com,ccc\n",
                        "text/csv"
                ));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN_403);

        verify(dao, never()).create(any(Organization.class), any(PersonProperties.class));
        verify(dao, never()).update(anyInt(), any(PersonProperties.class));
    }

    @Test
    public void batchUpdate_csv_happyPath() throws Exception {
        final Organization org = mockOrganization("org");

        final Person alice = mockPerson(org, "Alice");
        when(dao.read(eq(org), eq("aaa"))).thenReturn(alice);
        final ArgumentCaptor<PersonProperties> updateCaptor = ArgumentCaptor.forClass(PersonProperties.class);
        when(dao.update(eq(alice.getId()), updateCaptor.capture())).thenReturn(alice);

        final Person bob = mockPerson(org, "Bob");
        when(dao.read(eq(org), eq("bbb"))).thenReturn(bob);

        final Person carol = mockPerson(org, "Carol");
        when(dao.read(eq(org), eq("ccc"))).thenThrow(new ObjectNotFoundException());
        final ArgumentCaptor<PersonProperties> createCaptor = ArgumentCaptor.forClass(PersonProperties.class);
        when(dao.create(any(Organization.class), createCaptor.capture())).thenReturn(carol);

        final Group groupDev = mockGroup(org, "Developers");
        final Group groupMgr = mockGroup(org, "Managers");
        when(groupsDao.create(eq(org), any()))
                .thenReturn(groupDev)
                .thenReturn(groupMgr);
        when(groupsDao.read(eq(org), eq("Developers")))
                .thenThrow(new ObjectNotFoundException())
                .thenReturn(groupDev);
        when(groupsDao.read(eq(org), eq("Managers")))
                .thenThrow(new ObjectNotFoundException())
                .thenReturn(groupMgr);

        final Response response = resources
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.entity("" +
                                "name,attr.tag,email,custom_identifier,groups\n" +
                                "Alicia,boss,alice@example.com,aaa,Developers\n" +
                                "Carol,minion,carol@example.com,ccc,\"Developers , Managers \"\n",
                        "text/csv"
                ));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);

        final List<PersonBaseDTO> dto = response.readEntity(new GenericType<List<PersonBaseDTO>>() {
        });
        assertThat(dto).hasSize(2);
        assertThat(dto.get(0).id).isNotNull();
        assertThat(dto.get(0).name).isNotNull();
        assertThat(dto.get(1).id).isNotNull();
        assertThat(dto.get(1).name).isNotNull();

        verify(dao).read(eq(org), eq("ccc"));
        verify(dao).create(eq(org), any(PersonProperties.class));
        assertThat(createCaptor.getValue().getCustomIdentifier()).isEqualTo("ccc");
        assertThat(createCaptor.getValue().getName()).isEqualTo("Carol");
        assertThat(createCaptor.getValue().getEmail()).isEqualTo("carol@example.com");
        assertThat(createCaptor.getValue().getAttributes()).contains(new PersonAttribute("tag", "minion"));

        verify(dao).read(eq(org), eq("aaa"));
        verify(dao).update(eq(alice.getId()), any(PersonProperties.class));
        assertThat(updateCaptor.getValue().getCustomIdentifier()).isEqualTo("aaa");
        assertThat(updateCaptor.getValue().getName()).isEqualTo("Alicia");
        assertThat(updateCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(updateCaptor.getValue().getAttributes()).contains(new PersonAttribute("tag", "boss"));

        verify(organizationsDao).read(eq(org.getId()));
        verify(groupsDao, times(2)).read(eq(org), eq("Developers"));
        verify(groupsDao, times(1)).read(eq(org), eq("Managers"));
        verify(groupsDao, times(2)).create(eq(org), any());
        verify(membershipsDao).add(eq(alice), eq(groupDev), any());
        verify(membershipsDao).add(eq(carol), eq(groupDev), any());
        verify(membershipsDao).add(eq(carol), eq(groupMgr), any());
    }

    @Test
    public void batchUpdate_csv_updateSelf_expectBadRequest() throws Exception {
        final Organization org = mockOrganization("org");

        // Some mocking which maybe should be moved to MockUtil
        final Person editorPerson = credentialsDao.get(CredentialsType.PASSWORD, USERNAME_EDITOR).getPerson();
        final Organization editorOrganization = editorPerson.getOrganization();
        when(organizationsDao.read(eq(editorOrganization.getId()))).thenReturn(editorOrganization);

        //SUT 1: Batch update using custom identifier
        when(dao.read(any(Organization.class), eq("alice_editor"))).thenReturn(editorPerson);

        final Response response1 = resources
                .target("/organizations/" + UuidString.toString(editorOrganization.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.entity("" +
                                "name,email,custom_identifier,id\n" +
                                "Alicia,alice@example.com,alice_editor,",
                        "text/csv"
                ));

        assertThat(response1.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);

        //SUT 1: Batch update using primary key value
        when(dao.read(eq(editorPerson.getId()))).thenReturn(editorPerson);

        final Response response2 = resources
                .target("/organizations/" + UuidString.toString(editorOrganization.getId()) + "/people")
                .register(MockUtil.AUTH_FEATURE_EDITOR)
                .request()
                .put(Entity.entity("" +
                                "name,email,custom_identifier,id\n" +
                                "Alicia,alice@example.com,," + editorPerson.getId(),
                        "text/csv"
                ));

        assertThat(response2.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST_400);

        verify(dao, never()).create(eq(org), any(PersonProperties.class));
        verify(dao, never()).update(anyInt(), any(PersonProperties.class));
        verify(dao, never()).delete(anyInt());
    }

    @Test
    public void achievementSummary_twoAchievementTwoSteps_successful() throws Exception {
        final Organization org = mockOrganization("Alice's Organization");
        final Person person1 = mockPerson(org, "Alice");
        final Person person2 = mockPerson(org, "Bob");
        final Person person3 = mockPerson(org, "Carol");
        final AchievementStepProgress a1p1 = mockProgress(true, person1);
        final AchievementStepProgress a1p2 = mockProgress(true, person2);
        final AchievementStepProgress a1p3 = mockProgress(true, person1);
        final AchievementStepProgress a1p4 = mockProgress(true, person2);
        final AchievementStep a1s1 = mockStep(a1p1, a1p2);
        final AchievementStep a1s2 = mockStep(a1p3, a1p4);
        final Achievement a1 = mockAchievement("Climb mountain", a1s1, a1s2);

        final AchievementStepProgress a2p1 = mockProgress(false, person2);
        final AchievementStepProgress a2p2 = mockProgress(true, person1);
        final AchievementStepProgress a2p3 = mockProgress(false, person2);
        final AchievementStepProgress a2p4 = mockProgress(false, person1);
        final AchievementStep s2 = mockStep(a2p1, a2p2);
        final AchievementStep s3 = mockStep(a2p3, a2p4);
        final Achievement a2 = mockAchievement("Cook egg", s2, s3);

        when(achievementsDao.findWithProgressForPerson(eq(person1)))
                .thenReturn(Arrays.asList(a1, a2));

        final OrganizationAchievementSummaryDTO dto = resources.client()
                .target("/organizations/" + UuidString.toString(org.getId()) + "/people/" + person1.getId() + "/achievement-summary")
                .register(MockUtil.AUTH_FEATURE_READER)
                .request()
                .get(OrganizationAchievementSummaryDTO.class);

        assertThat(dto.achievements).hasSize(2);

        assertThat(dto.achievements.get(0).achievement.name).isEqualTo("Climb mountain");
        assertThat(dto.achievements.get(0).progress_summary.people_completed).isEqualTo(1);
        assertThat(dto.achievements.get(0).progress_summary.people_started).isEqualTo(0);
        assertThat(dto.achievements.get(0).progress_detailed).hasSize(1);
        assertThat(dto.achievements.get(0).progress_detailed.get(0).person.name).isEqualTo("Alice");
        assertThat(dto.achievements.get(0).progress_detailed.get(0).percent).isEqualTo(100);

        assertThat(dto.achievements.get(1).achievement.name).isEqualTo("Cook egg");
        assertThat(dto.achievements.get(1).progress_summary.people_completed).isEqualTo(0);
        assertThat(dto.achievements.get(1).progress_summary.people_started).isEqualTo(1);
        assertThat(dto.achievements.get(1).progress_detailed).hasSize(1);
        assertThat(dto.achievements.get(1).progress_detailed.get(0).person.name).isEqualTo("Alice");
        assertThat(dto.achievements.get(1).progress_detailed.get(0).percent).isEqualTo(50);
    }

    private Person mockPerson(Organization org, String name) throws ObjectNotFoundException, IOException {
        return mockPerson(org, name, null);
    }

    private Person mockPerson(Organization org, String name, String customId) throws ObjectNotFoundException, IOException {
        final Person person1 = MockUtil.mockPerson(org, name, customId, Roles.READER);
        when(dao.read(eq(person1.getId()))).thenReturn(person1);
        final PasswordValidator passwordValidator = new PasswordValidator(SecretGenerator.PDKDF2, "password".toCharArray());
        final Credentials credentials = new Credentials("username", passwordValidator.getCredentialsType(), passwordValidator.getCredentialsData());
        when(credentialsDao.get(eq(CredentialsType.PASSWORD), eq(name))).thenReturn(credentials);
        return person1;
    }

/*
    private Group mockGroup(Organization org, String name) throws ObjectNotFoundException, IOException {
        final Group grp = MockUtil.mockGroup(org, name);
        when(dao.read(eq(person1.getId()))).thenReturn(person1);
        final PasswordValidator passwordValidator = new PasswordValidator(SecretGenerator.PDKDF2, "password".toCharArray());
        final Credentials credentials = new Credentials("username", passwordValidator.getCredentialsType(), passwordValidator.getCredentialsData());
        when(credentialsDao.get(eq(CredentialsType.PASSWORD), eq(name))).thenReturn(credentials);
        return person1;
    }
*/
}