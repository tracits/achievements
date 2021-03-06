package se.devscout.achievements.server.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import se.devscout.achievements.dataimporter.SlugGenerator;
import se.devscout.achievements.server.api.AchievementDTO;
import se.devscout.achievements.server.api.AchievementStepDTO;
import se.devscout.achievements.server.data.dao.AchievementStepsDao;
import se.devscout.achievements.server.data.dao.AchievementsDao;
import se.devscout.achievements.server.data.dao.DaoException;
import se.devscout.achievements.server.data.dao.ObjectNotFoundException;
import se.devscout.achievements.server.data.model.Achievement;
import se.devscout.achievements.server.data.model.AchievementProperties;
import se.devscout.achievements.server.data.model.AchievementStepProperties;
import se.devscout.achievements.server.resources.UuidString;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ImportScouternaBadgesTask extends DatabaseTask {

    private static final SlugGenerator slugGenerator = new SlugGenerator();
    private static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private static ObjectReader yamlReader = mapper.readerFor(AchievementDTO.class);

    private static int i;

    final HashMap<String, AchievementDTO> slugToDto = new HashMap<>();

    private final AchievementsDao achievementsDao;
    private final AchievementStepsDao achievementStepsDao;

    public ImportScouternaBadgesTask(SessionFactory sessionFactory, AchievementsDao achievementsDao, AchievementStepsDao achievementStepsDao) {
        super("import-scouterna", sessionFactory);
        this.achievementsDao = achievementsDao;
        this.achievementStepsDao = achievementStepsDao;
    }

    @Override
    protected void execute(ImmutableMultimap<String, String> parameters, PrintWriter output, Session session) throws Exception {
        Reflections reflections = new Reflections("achievements.scouterna_se", new ResourcesScanner());
        Set<String> fileNames = reflections.getResources(Pattern.compile(".*\\.yaml"));
        for (String fileName : fileNames) {
            output.println(fileName);
        }

        final List<AchievementDTO> achievements = fileNames.stream().map(ImportScouternaBadgesTask::toAchievement)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        achievements.forEach(a -> a.slug = slugGenerator.toSlug(a.name));
        achievements.stream().map(a -> a.name).forEach(output::println);

        for (AchievementDTO achievement : achievements) {
            slugToDto.put(achievement.slug, achievement);
        }

        final HashMap<String, String> slugToPrerequisite = new HashMap<>();
        for (AchievementDTO achievement : achievements) {
            achievement.steps.stream()
                    .filter(step -> step.prerequisite_achievement != null)
                    .findFirst()
                    .ifPresent(step -> slugToPrerequisite.put(
                            achievement.slug,
                            step.prerequisite_achievement));
        }

        for (AchievementDTO dto : slugToDto.values()) {
            if (dto.id == null) {
                processOne(dto, output);
            }
        }
        output.println(slugToPrerequisite);
    }

    private void processOne(AchievementDTO dto, PrintWriter output) {
        dto.steps.stream()
                .filter(step -> step.prerequisite_achievement != null)
                .findFirst()
                .map(step -> step.prerequisite_achievement)
                .ifPresent(s -> processOne(slugToDto.get(s), output));

        final AchievementProperties properties = new AchievementProperties();
        properties.setDescription(dto.description);
        properties.setImage(dto.image);
        properties.setName(dto.name);
        if (dto.tags != null) {
            properties.setTags(Sets.newHashSet(dto.tags));
        }
        final Set<ConstraintViolation<AchievementProperties>> violations = Validation.buildDefaultValidatorFactory().getValidator().validate(properties);
        if (violations.isEmpty()) {
            try {
                final Optional<Achievement> existing = achievementsDao.find(dto.name).stream().filter(a -> a.getName().equalsIgnoreCase(dto.name)).findFirst();
                if (!existing.isPresent()) {
                    final Achievement achievement = achievementsDao.create(properties);
                    dto.id = new UuidString(achievement.getId()).getValue();
                    if (dto.steps != null) {
                        for (AchievementStepDTO stepDto : dto.steps) {
                            final AchievementStepProperties stepProperties = new AchievementStepProperties();
                            if (!Strings.isNullOrEmpty(stepDto.prerequisite_achievement)) {
                                try {
                                    final Achievement prerequisiteAchievement = achievementsDao.read(new UuidString(slugToDto.get(stepDto.prerequisite_achievement).id).getUUID());
                                    stepProperties.setPrerequisiteAchievement(prerequisiteAchievement);
                                } catch (ObjectNotFoundException e) {
                                    e.printStackTrace(output);
                                }
                            } else {
                                stepProperties.setDescription(stepDto.description);
                            }
                            try {
                                achievementStepsDao.create(achievement, stepProperties);
                            } catch (DaoException e) {
                                e.printStackTrace(output);
                            }
                        }
                    }
                } else {
                    dto.id = new UuidString(existing.get().getId()).getValue();
                    output.println("Skipped badge " + dto.name + " because is already exists");
                }
            } catch (DaoException e) {
                e.printStackTrace(output);
            }
        } else {
            output.println("Skipped badge because of " + violations.stream().map(violation -> violation.getMessage()).collect(Collectors.joining(", ")));
        }

        output.println("Saving " + dto.slug + " as " + dto.id);
    }

    private static AchievementDTO toAchievement(String fileName) {
        try {
            final String raw = Resources.asCharSource(Resources.getResource(fileName), Charsets.UTF_8).read();
            return yamlReader.readValue(raw);
        } catch (IOException e) {
            return null;
        }
    }

}
