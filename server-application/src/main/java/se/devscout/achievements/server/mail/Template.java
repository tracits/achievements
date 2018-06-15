package se.devscout.achievements.server.mail;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;

public class Template {

    private final Mustache mustache;

    public Template(String resourcePath) {
        try (Reader reader = Resources.asCharSource(Resources.getResource(resourcePath), Charsets.UTF_8).openStream()) {
            this.mustache = new DefaultMustacheFactory().compile(reader, "signin");
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String render(Map<String, String> parameters) {
        StringWriter writer = new StringWriter();
        mustache.execute(writer, parameters);
        return writer.getBuffer().toString();
    }
}
