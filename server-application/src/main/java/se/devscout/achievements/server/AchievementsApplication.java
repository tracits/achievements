package se.devscout.achievements.server;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.hibernate.SessionFactory;
import se.devscout.achievements.server.cli.BoostrapDataTask;
import se.devscout.achievements.server.data.dao.AchievementsDaoImpl;
import se.devscout.achievements.server.data.dao.OrganizationsDaoImpl;
import se.devscout.achievements.server.data.dao.PeopleDaoImpl;
import se.devscout.achievements.server.data.model.*;
import se.devscout.achievements.server.health.IsAliveHealthcheck;
import se.devscout.achievements.server.resources.AchievementsResource;
import se.devscout.achievements.server.resources.OrganizationsResource;
import se.devscout.achievements.server.resources.PeopleResource;
import se.devscout.achievements.server.resources.StatsResource;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class AchievementsApplication extends Application<AchievementsApplicationConfiguration> {
    private final HibernateBundle<AchievementsApplicationConfiguration> hibernate = new HibernateBundle<AchievementsApplicationConfiguration>(
            Organization.class,
            Person.class,
            Achievement.class,
            AchievementStep.class
    ) {
        public DataSourceFactory getDataSourceFactory(AchievementsApplicationConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };

    public void run(AchievementsApplicationConfiguration config, Environment environment) throws Exception {
        final SessionFactory sessionFactory = hibernate.getSessionFactory();

        final OrganizationsDaoImpl organizationsDao = new OrganizationsDaoImpl(sessionFactory, config.getMaxOrganizationCount());
        final AchievementsDaoImpl achievementsDao = new AchievementsDaoImpl(sessionFactory);
        final PeopleDaoImpl peopleDao = new PeopleDaoImpl(sessionFactory);

        environment.jersey().register(new OrganizationsResource(organizationsDao));
        environment.jersey().register(new AchievementsResource(achievementsDao));
        environment.jersey().register(new PeopleResource(peopleDao, organizationsDao));
        environment.jersey().register(new StatsResource(organizationsDao));

        environment.healthChecks().register("alive", new IsAliveHealthcheck());

        environment.admin().addTask(new BoostrapDataTask(sessionFactory, organizationsDao, peopleDao));

        initCorsHeaders(environment);
    }

    private void initCorsHeaders(Environment env) {
        FilterRegistration.Dynamic filter = env.servlets().addFilter("CORSFilter", CrossOriginFilter.class);
        filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, env.getApplicationContext().getContextPath() + "*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        filter.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "Origin, Content-Type, Accept, Authorization");
        filter.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");
    }

    @Override
    public String getName() {
        return "achievements";
    }

    @Override
    public void initialize(Bootstrap<AchievementsApplicationConfiguration> bootstrap) {
        bootstrap.addBundle(hibernate);
        bootstrap.addBundle(new AssetsBundle("/assets/", "/"));
    }

    public static void main(String[] args) throws Exception {
        new AchievementsApplication().run(args);
    }

}
