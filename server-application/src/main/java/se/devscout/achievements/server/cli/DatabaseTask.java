package se.devscout.achievements.server.cli;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.servlets.tasks.Task;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;

import java.io.PrintWriter;

public abstract class DatabaseTask extends Task {
    private final SessionFactory sessionFactory;

    DatabaseTask(String name, SessionFactory sessionFactory) {
        super(name);
        this.sessionFactory = sessionFactory;
    }

    @Override
    public final void execute(ImmutableMultimap<String, String> parameters, PrintWriter output) throws Exception {

        Session session = sessionFactory.openSession();
        try {
            ManagedSessionContext.bind(session);
            Transaction transaction = session.beginTransaction();
            try {

                execute(parameters, output, session);

                transaction.commit();
            } catch (Exception e) {
                transaction.rollback();
                throw new RuntimeException(e);
            }
        } finally {
            session.close();
            ManagedSessionContext.unbind(sessionFactory);
        }
        output.flush();
        output.close();
    }

    protected abstract void execute(ImmutableMultimap<String, String> parameters, PrintWriter output, Session session) throws Exception;
}
