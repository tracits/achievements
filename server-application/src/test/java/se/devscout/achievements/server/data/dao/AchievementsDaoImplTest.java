package se.devscout.achievements.server.data.dao;

import com.google.common.collect.Sets;
import io.dropwizard.testing.junit.DAOTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import se.devscout.achievements.server.auth.Roles;
import se.devscout.achievements.server.data.model.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class AchievementsDaoImplTest {
    @Rule
    public DAOTestRule database = DAOTestRule.newBuilder()
            .setShowSql(true)
            .addEntityClass(Organization.class)
            .addEntityClass(Person.class)
            .addEntityClass(GroupMembership.class)
            .addEntityClass(Group.class)
            .addEntityClass(Credentials.class)
            .addEntityClass(Achievement.class)
            .addEntityClass(AchievementStep.class)
            .addEntityClass(AchievementStepProgress.class)
            .addEntityClass(StepProgressAuditRecord.class)
            .build();

    private AchievementsDaoImpl dao;
    private PeopleDaoImpl peopleDao;

    @Before
    public void setUp() throws Exception {
        dao = new AchievementsDaoImpl(database.getSessionFactory());
        peopleDao = new PeopleDaoImpl(database.getSessionFactory());
    }

    @Test
    public void findWithProgressForOrganizationAndPerson() throws Exception {
        // Setup: Create organization
        OrganizationsDaoImpl organizationDao = new OrganizationsDaoImpl(database.getSessionFactory(), 100L);
        Organization org1 = database.inTransaction(() -> organizationDao.create(new OrganizationProperties("Test Organization 1")));
        Organization org2 = database.inTransaction(() -> organizationDao.create(new OrganizationProperties("Test Organization 2")));

        // Setup: Crate people
        PeopleDaoImpl peopleDao = new PeopleDaoImpl(database.getSessionFactory());
        Person personAliceWithProgress = database.inTransaction(() -> peopleDao.create(org1, new PersonProperties("Alice", Roles.READER)));
        Person personAlanWithProgress = database.inTransaction(() -> peopleDao.create(org1, new PersonProperties("Alan", Roles.READER)));
        Person personWithoutProgress = database.inTransaction(() -> peopleDao.create(org1, new PersonProperties("Bob", Roles.READER)));
        Person personFromOtherOrgWithProgress = database.inTransaction(() -> peopleDao.create(org2, new PersonProperties("Carol", Roles.READER)));

        // Setup: Create achievements
        AchievementsDaoImpl achievementsDao = dao;
        final Achievement achievement1 = database.inTransaction(() -> achievementsDao.create(new AchievementProperties("Boil an egg")));
        final Achievement achievement2 = database.inTransaction(() -> achievementsDao.create(new AchievementProperties("Cook pasta")));
        final Achievement achievement3 = database.inTransaction(() -> achievementsDao.create(new AchievementProperties("Have pizza")));

        // Setup: Create achievement steps
        AchievementStepsDaoImpl stepsDao = new AchievementStepsDaoImpl(database.getSessionFactory());
        AchievementStep achievement1Step1 = database.inTransaction(() -> stepsDao.create(achievement1, new AchievementStepProperties("Follow the instructions on the package")));
        AchievementStep achievement1Step2 = database.inTransaction(() -> stepsDao.create(achievement1, new AchievementStepProperties("Clean up afterwards")));
        AchievementStep achievement2Step1 = database.inTransaction(() -> stepsDao.create(achievement2, new AchievementStepProperties("Pour water into pot")));
        AchievementStep achievement2Step2 = database.inTransaction(() -> stepsDao.create(achievement2, new AchievementStepProperties("Put pasta into pot")));
        AchievementStep achievement2Step3 = database.inTransaction(() -> stepsDao.create(achievement2, new AchievementStepProperties("Turn on stove")));
        AchievementStep achievement2Step4 = database.inTransaction(() -> stepsDao.create(achievement2, new AchievementStepProperties("Wait until it has boiled for the appropriate number of minutes")));
        AchievementStep achievement2Step5 = database.inTransaction(() -> stepsDao.create(achievement2, new AchievementStepProperties("Use colinder to pour out the water")));
        AchievementStep achievement2Step6 = database.inTransaction(() -> stepsDao.create(achievement2, new AchievementStepProperties("Serve with bolognese")));
        AchievementStep achievement3Step1 = database.inTransaction(() -> stepsDao.create(achievement3, new AchievementStepProperties("Order from local pizzeria")));
        AchievementStep achievement3Step2 = database.inTransaction(() -> stepsDao.create(achievement3, new AchievementStepProperties("Eat and enjoy")));

        // Setup: Create progress records
        AchievementStepProgressDaoImpl progressDao = new AchievementStepProgressDaoImpl(database.getSessionFactory());
        database.inTransaction(() -> progressDao.set(achievement1Step1, personAliceWithProgress, new AchievementStepProgressProperties(true, "Finally done")));
        database.inTransaction(() -> progressDao.set(achievement1Step2, personAliceWithProgress, new AchievementStepProgressProperties(false, "Still eating the egg")));
        database.inTransaction(() -> progressDao.set(achievement2Step1, personAliceWithProgress, new AchievementStepProgressProperties(true, null)));
        database.inTransaction(() -> progressDao.set(achievement2Step2, personAliceWithProgress, new AchievementStepProgressProperties(true, null)));
        database.inTransaction(() -> progressDao.set(achievement2Step3, personAliceWithProgress, new AchievementStepProgressProperties(true, null)));
        database.inTransaction(() -> progressDao.set(achievement2Step4, personAliceWithProgress, new AchievementStepProgressProperties(true, null)));
        database.inTransaction(() -> progressDao.set(achievement2Step5, personAliceWithProgress, new AchievementStepProgressProperties(true, null)));
        database.inTransaction(() -> progressDao.set(achievement3Step1, personFromOtherOrgWithProgress, new AchievementStepProgressProperties(true, null)));
        database.inTransaction(() -> progressDao.set(achievement3Step1, personAlanWithProgress, new AchievementStepProgressProperties(true, "It is a start")));

        System.out.println("SUT");

        // Testing ORGANIZATONS:

        final List<Achievement> actual1 = achievementsDao.findWithProgressForOrganization(org1);
        assertThat(actual1).hasSize(3);
        assertThat(actual1).containsOnly(achievement1, achievement2, achievement3);

        final List<Achievement> actual2 = achievementsDao.findWithProgressForOrganization(org2);
        assertThat(actual2).hasSize(1);
        assertThat(actual2).containsOnly(achievement3);

        // Testing PEOPLE:

        final List<Achievement> actual3a = database.inTransaction(() -> achievementsDao.findWithProgressForPerson(personAliceWithProgress));
        assertThat(actual3a).hasSize(2);
        assertThat(actual3a).containsOnly(achievement1, achievement2);

        final List<Achievement> actual3b = database.inTransaction(() -> achievementsDao.findWithProgressForPerson(personAlanWithProgress));
        assertThat(actual3b).hasSize(1);
        assertThat(actual3b).containsOnly(achievement3);
        assertThat(actual3b.get(0).getSteps()).hasSize(2);

        final List<Achievement> actual4 = achievementsDao.findWithProgressForPerson(personFromOtherOrgWithProgress);
        assertThat(actual4).hasSize(1);
        assertThat(actual4).containsOnly(achievement3);

        final List<Achievement> actual5 = achievementsDao.findWithProgressForPerson(personWithoutProgress);
        assertThat(actual5).isEmpty();
    }

    @Test
    public void get_happyPath() throws Exception {
        UUID aliceUuid = database.inTransaction(() -> dao.create(new AchievementProperties("Cook Pasta"))).getId();
        final Achievement actual = dao.read(aliceUuid);
        assertThat(actual.getName()).isEqualTo("Cook Pasta");
    }

    @Test
    public void find_singleWord_happyPath() throws Exception {
        UUID fire = database.inTransaction(() -> dao.create(new AchievementProperties("Make Fire"))).getId();
        UUID raft = database.inTransaction(() -> dao.create(new AchievementProperties("Make Raft"))).getId();
        UUID treasure = database.inTransaction(() -> dao.create(new AchievementProperties("Find Treasure"))).getId();
        final List<Achievement> actual = dao.find("Make");
        List<UUID> returnedUuids = actual.stream().map(Achievement::getId).collect(Collectors.toList());
        assertThat(returnedUuids).containsExactlyInAnyOrder(fire, raft);
    }

    @Test
    public void find_tag_happyPath() throws Exception {
        UUID fire = database.inTransaction(() -> dao.create(new AchievementProperties("Make Fire", "description", Sets.newHashSet("matches", "wood")))).getId();
        UUID raft = database.inTransaction(() -> dao.create(new AchievementProperties("Make Raft", "description", Sets.newHashSet("water", "boat")))).getId();
        final List<Achievement> actual = dao.find("boat");
        List<UUID> returnedUuids = actual.stream().map(Achievement::getId).collect(Collectors.toList());
        assertThat(returnedUuids).containsExactlyInAnyOrder(raft);
    }

    @Test
    public void find_twoWords_happyPath() {
        UUID fire = database.inTransaction(() -> dao.create(new AchievementProperties("Make Fire"))).getId();
        UUID raft = database.inTransaction(() -> dao.create(new AchievementProperties("Make Raft"))).getId();
        UUID treasure = database.inTransaction(() -> dao.create(new AchievementProperties("Find Treasure"))).getId();
        final List<Achievement> actual = dao.find("Make Raft");
        List<UUID> returnedUuids = actual.stream().map(Achievement::getId).collect(Collectors.toList());
        assertThat(returnedUuids).containsExactlyInAnyOrder(raft);
    }

    @Test
    public void find_quotedSentence_happyPath() {
        UUID fire = database.inTransaction(() -> dao.create(new AchievementProperties("aab ccd eef"))).getId();
        UUID raft = database.inTransaction(() -> dao.create(new AchievementProperties("aab iij kkl"))).getId();
        UUID treasure = database.inTransaction(() -> dao.create(new AchievementProperties("mmn oop qqr"))).getId();
        final List<Achievement> actual = dao.find("\"aab ccd\"");
        List<UUID> returnedUuids = actual.stream().map(Achievement::getId).collect(Collectors.toList());
        assertThat(returnedUuids).containsExactlyInAnyOrder(fire);
    }

    @Test
    public void find_caseInsensitive_happyPath() {
        UUID fire1 = database.inTransaction(() -> dao.create(new AchievementProperties("make fire"))).getId();
        UUID fire2 = database.inTransaction(() -> dao.create(new AchievementProperties("MAKE FIRE"))).getId();
        UUID treasure = database.inTransaction(() -> dao.create(new AchievementProperties("Find Treasure"))).getId();
        final List<Achievement> actual = dao.find("mAkE FiRe");
        List<UUID> returnedUuids = actual.stream().map(Achievement::getId).collect(Collectors.toList());
        assertThat(returnedUuids).containsExactlyInAnyOrder(fire1, fire2);
    }

    @Test
    public void readAll_achievementsExists_happyPath() throws Exception {
        UUID fire = database.inTransaction(() -> dao.create(new AchievementProperties("Make Fire"))).getId();
        UUID raft = database.inTransaction(() -> dao.create(new AchievementProperties("Make Raft"))).getId();
        UUID treasure = database.inTransaction(() -> dao.create(new AchievementProperties("Find Treasure"))).getId();
        final List<Achievement> actual = dao.readAll();
        List<UUID> returnedUuids = actual.stream().map(Achievement::getId).collect(Collectors.toList());
        assertThat(returnedUuids).containsExactlyInAnyOrder(fire, raft, treasure);
    }

    @Test
    public void readAll_emptyDatabase_happyPath() throws Exception {
        final List<Achievement> actual = dao.readAll();
        assertThat(actual).isNotNull();
        assertThat(actual).isEmpty();
    }

    @Test
    public void find_noCondition_expectException() throws Exception {
        for (String name : new String[]{null, "", "\t", "     ", "\n"}) {
            try {
                dao.find(name);
                fail("Expected IllegalArgumentException when using condition '" + name + "'.");
            } catch (IllegalArgumentException e) {
                // Expected.
            }
        }
    }

    @Test(expected = ObjectNotFoundException.class)
    public void get_notFound() throws Exception {
        dao.read(UUID.randomUUID());
    }

    @Test
    public void delete_happyPath() throws Exception {
        UUID id = database.inTransaction(() -> dao.create(new AchievementProperties("Something"))).getId();
        database.inTransaction(() -> {
            try {
                dao.delete(id);
            } catch (ObjectNotFoundException e) {
                fail();
            }
        });
        database.inTransaction(() -> {
            try {
                dao.read(id);
                fail();
            } catch (ObjectNotFoundException e) {
            }
        });
    }

    @Test(expected = ObjectNotFoundException.class)
    public void delete_notFound() throws Exception {
        dao.delete(UUID.randomUUID());
    }

    @Test
    public void create_happyPath() throws Exception {
        final Achievement result = database.inTransaction(() -> dao.create(new AchievementProperties("Boil Egg")));
        final Achievement actual = database.inTransaction(() -> dao.read(result.getId()));
        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getName()).isEqualTo("Boil Egg");
    }

    @Test
    public void update_simpleAchievement_happyPath() throws Exception {
        UUID objectUuid = database.inTransaction(() -> dao.create(new AchievementProperties("Cook Pasta"))).getId();

        database.inTransaction(() -> dao.update(objectUuid, new AchievementProperties("Cook Spagetti")));

        final Achievement actual = database.inTransaction(() -> dao.read(objectUuid));
        assertThat(actual.getId()).isEqualTo(objectUuid);
        assertThat(actual.getName()).isEqualTo("Cook Spagetti");
    }

    @Test
    public void awardedTo_happyPath() throws Exception {
        // Setup: Create achievements
        final Achievement achievementPasta = database.inTransaction(() -> dao.create(new AchievementProperties("Cook Pasta")));
        final Achievement achievementEgg = database.inTransaction(() -> dao.create(new AchievementProperties("Cook Egg")));

        // Setup: Create organization
        OrganizationsDaoImpl organizationDao = new OrganizationsDaoImpl(database.getSessionFactory(), 100L);
        Organization org = database.inTransaction(() -> organizationDao.create(new OrganizationProperties("Test Organization")));

        // Setup: Crate people
        Person personAlice = database.inTransaction(() -> peopleDao.create(org, new PersonProperties("Alice", Roles.READER)));
        Person personBob = database.inTransaction(() -> peopleDao.create(org, new PersonProperties("Bob", Roles.READER)));

        // TEST addAwardedTo
        database.inTransaction(() -> dao.addAwardedTo(achievementEgg, personAlice));
        database.inTransaction(() -> dao.addAwardedTo(achievementEgg, personBob));
        database.inTransaction(() -> dao.addAwardedTo(achievementPasta, personAlice));

        verifyIsAwardedTo(achievementEgg.getId(), personAlice, personBob);
        verifyIsAwardedTo(achievementPasta.getId(), personAlice);
        verifyHasBeenAwarded(personAlice.getId(), achievementEgg, achievementPasta);
        verifyHasBeenAwarded(personBob.getId(), achievementEgg);

        // TEST addAwardedTo
        database.inTransaction(() -> dao.addAwardedTo(achievementPasta, personBob));

        verifyIsAwardedTo(achievementEgg.getId(), personAlice, personBob);
        verifyIsAwardedTo(achievementPasta.getId(), personAlice, personBob);
        verifyHasBeenAwarded(personAlice.getId(), achievementEgg, achievementPasta);
        verifyHasBeenAwarded(personBob.getId(), achievementEgg, achievementPasta);

        // TEST removeAwardedTo
        database.inTransaction(() -> dao.removeAwardedTo(achievementPasta, personAlice));
        database.inTransaction(() -> dao.removeAwardedTo(achievementPasta, personBob));
        database.inTransaction(() -> dao.removeAwardedTo(achievementEgg, personBob));

        verifyIsAwardedTo(achievementEgg.getId(), personAlice);
        verifyIsAwardedTo(achievementPasta.getId());
        verifyHasBeenAwarded(personAlice.getId(), achievementEgg);
        verifyHasBeenAwarded(personBob.getId());
    }

    private void verifyHasBeenAwarded(int id, Achievement... achievements) {
        final Person actual = database.inTransaction(() -> peopleDao.read(id));
        assertThat(actual.getId()).isEqualTo(id);
        assertThat(actual.getAwards()).containsOnly(achievements);
    }

    private void verifyIsAwardedTo(UUID id, Person... people) {
        final Achievement actual = database.inTransaction(() -> dao.read(id));
        assertThat(actual.getId()).isEqualTo(id);
        assertThat(actual.getAwardedTo()).containsOnly(people);
    }

    @Test
    public void update_complexAchievement_happyPath() throws Exception {
        UUID objectUuid = database.inTransaction(() -> {
            final AchievementProperties initialProperties = new AchievementProperties(
                    "Cook Pasta",
                    Sets.newHashSet("italian", "simple", "tasty"));
            return dao.create(initialProperties);
        }).getId();

        database.inTransaction(() -> {
            final AchievementProperties updatedProperties = new AchievementProperties(
                    "Cook Spagetti",
                    Sets.newHashSet("italian", "quick"));
            return dao.update(objectUuid, updatedProperties);
        });

        final Achievement actual = database.inTransaction(() -> dao.read(objectUuid));
        assertThat(actual.getId()).isEqualTo(objectUuid);
        assertThat(actual.getName()).isEqualTo("Cook Spagetti");
        assertThat(actual.getTags()).containsExactlyInAnyOrder("italian", "quick");
    }
}