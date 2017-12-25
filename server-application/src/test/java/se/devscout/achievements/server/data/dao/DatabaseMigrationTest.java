package se.devscout.achievements.server.data.dao;

import com.google.common.io.Resources;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.testing.junit.DropwizardAppRule;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import se.devscout.achievements.server.AchievementsApplication;
import se.devscout.achievements.server.AchievementsApplicationConfiguration;
import se.devscout.achievements.server.TestUtil;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertTrue;

public class DatabaseMigrationTest {
    @ClassRule
    public static final DropwizardAppRule<AchievementsApplicationConfiguration> RULE =
            new DropwizardAppRule(
                    AchievementsApplication.class,
                    Resources.getResource("server-test-configuration.yaml").getPath(),
//                    TestUtil.getApplicationPortOverride(),
//                    TestUtil.getAdminPortOverride(),
                    TestUtil.NO_HBM_2_DDL,
                    TestUtil.getPrivateDatabase()
            );
    private static Liquibase migrator;
    private static ManagedDataSource ds;

    // Credits: http://stackoverflow.com/questions/23579607/run-migrations-programmatically-in-dropwizard
    // Credits: http://forum.liquibase.org/topic/using-hsqldb-and-liquibase-for-unit-test
    @BeforeClass
    public static void setUp() throws Exception {
        DataSourceFactory dataSourceFactory = RULE.getConfiguration().getDataSourceFactory();
        ds = dataSourceFactory.build(RULE.getEnvironment().metrics(), "migrations");
    }

    /**
     * Verifies that all database migrations can be rolled back (according to the migrations.xml file).
     */
    @Test
    public void DatabaseMigration_CanBeRolledBack_HappyPath() throws Exception {
        try (Connection connection = ds.getConnection()) {
            initMigrator(connection);
        }
        // ONLY add a change set to this list if you are ABSOLUTELY sure that there is no risk that we need to roll it back.
        List<String> ignoredChangeSetIds = Arrays.asList(
        );

        List<ChangeSet> allChangeSets = migrator.getDatabaseChangeLog().getChangeSets();

        Predicate<ChangeSet> nonIgnoredChangeSets = cs -> !ignoredChangeSetIds.contains(cs.getId());
        Predicate<ChangeSet> changeSetsWithoutRollbackCommand = cs -> cs.getRollback().getChanges().size() == 0;

        assertTrue("Change sets must have <rollback> definition OR changes must have built-in rollback support",
                allChangeSets.stream()
                        .filter(nonIgnoredChangeSets)
                        .filter(changeSetsWithoutRollbackCommand)
                        .flatMap(cs -> cs.getChanges().stream())
                        .allMatch(change -> change.supportsRollback(migrator.getDatabase())));
    }

    @Test
    public void DatabaseMigration_ValidMigrations_HappyPath() throws Exception {
        try (Connection connection = ds.getConnection()) {
            initMigrator(connection);
            migrator.update("");
        }
    }

    void initMigrator(Connection connection) throws LiquibaseException {
        migrator = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
    }

}
