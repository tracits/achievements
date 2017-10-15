package se.devscout.achievements.server;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import se.devscout.achievements.server.api.AchievementDTO;
import se.devscout.achievements.server.auth.PasswordValidator;
import se.devscout.achievements.server.auth.SecretGenerator;
import se.devscout.achievements.server.data.dao.AchievementStepProgressDao;
import se.devscout.achievements.server.data.dao.AchievementsDao;
import se.devscout.achievements.server.data.dao.CredentialsDao;
import se.devscout.achievements.server.data.model.*;
import se.devscout.achievements.server.resources.AchievementsResource;
import se.devscout.achievements.server.uti.User;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class AchievementsResourceTest {

    private final AchievementsDao dao = mock(AchievementsDao.class);
    private final AchievementStepProgressDao progressDao = mock(AchievementStepProgressDao.class);

    private final CredentialsDao credentialsDao = mock(CredentialsDao.class);

    @Rule
    public final ResourceTestRule resources = ResourceTestRule.builder()
//            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addProvider(AchievementsApplication.createAuthFeature(mock(SessionFactory.class), credentialsDao))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(User.class))

            .addResource(new AchievementsResource(dao, progressDao))
            .build();

    @Before
    public void setUp() throws Exception {
        final Credentials credentials = new Credentials("username", new PasswordValidator(SecretGenerator.PDKDF2, "password".toCharArray()));
        when(credentialsDao.get(eq(IdentityProvider.PASSWORD), eq("user"))).thenReturn(credentials);
    }

    @Test
    public void get_happyPath() throws Exception {
        final Achievement achievement = mock(Achievement.class);
        final UUID uuid = UUID.randomUUID();
        when(achievement.getId()).thenReturn(uuid);
        when(dao.read(eq(uuid))).thenReturn(achievement);
        final Response response = resources
                .target("/achievements/" + uuid.toString())
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode("user:password".getBytes(Charsets.UTF_8)))
                .get();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK_200);
        final AchievementDTO dto = response.readEntity(AchievementDTO.class);
        assertThat(dto.id).isEqualTo(achievement.getId().toString());
    }

    @Test
    public void get_notFound() throws Exception {
        when(dao.read(any(UUID.class))).thenThrow(new NotFoundException());
        final Response response = resources
                .target("/achievements/id")
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode("user:password".getBytes(Charsets.UTF_8)))
                .get();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void delete_notFound() throws Exception {
        doThrow(new NotFoundException()).when(dao).delete(any(UUID.class));
        final Response response = resources
                .target("/achievements/id")
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode("user:password".getBytes(Charsets.UTF_8)))
                .delete();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void delete_happyPath() throws Exception {
        final Achievement achievement = mock(Achievement.class);
        final UUID uuid = UUID.randomUUID();
        when(achievement.getId()).thenReturn(uuid);
        when(dao.read(eq(uuid))).thenReturn(achievement);
        final Response response = resources
                .target("/achievements/" + uuid.toString())
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode("user:password".getBytes(Charsets.UTF_8)))
                .delete();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void create_happyPath() throws Exception {
        final Achievement achievement = mock(Achievement.class);
        when(achievement.getId()).thenReturn(UUID.randomUUID());
        when(achievement.getName()).thenReturn("abc");
        when(dao.create(any(AchievementProperties.class))).thenReturn(achievement);
        final Response response = resources
                .target("/achievements")
                .request()
                .header(HttpHeaders.AUTHORIZATION, "Basic " + BaseEncoding.base64().encode("user:password".getBytes(Charsets.UTF_8)))
                .post(Entity.json(new AchievementDTO()));
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED_201);
        final AchievementDTO dto = response.readEntity(AchievementDTO.class);
        assertThat(response.getLocation().getPath()).isEqualTo("/achievements/" + dto.id);
        assertThat(dto.id).isEqualTo(achievement.getId().toString());
        assertThat(dto.name).isEqualTo("abc");
    }

}