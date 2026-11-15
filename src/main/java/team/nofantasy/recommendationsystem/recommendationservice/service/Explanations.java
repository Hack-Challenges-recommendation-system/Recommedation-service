package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.springframework.util.StringUtils;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto.HistorySource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reasons, next steps and the order of a list written from the signals of the candidates: for the baseline, and for
 * gaps in an LLM answer. Every item of a list must say why it was picked and what to do next.
 */
final class Explanations {
    private static final Map<String, String> STAGES = Map.of(
            "IDEA", "для тех, кто только задумал своё дело",
            "LAUNCH", "для запуска бизнеса",
            "GROWTH", "для развития бизнеса",
            "SCALING", "для масштабирования бизнеса"
    );
    private static final Map<String, String> NEXT_STEPS = Map.of(
            "ARTICLE", "Прочитайте и выпишите одну идею, которую примените на этой неделе",
            "VIDEO", "Посмотрите видео и запишите первый шаг для своего бизнеса",
            "COURSE", "Начните с первого модуля курса",
            "WEBINAR", "Посмотрите запись и запишите вопросы к своему бизнесу",
            "TEMPLATE", "Заполните шаблон для своего бизнеса",
            "CHECKLIST", "Пройдите чек-лист и отметьте, что уже сделано",
            "PODCAST", "Послушайте выпуск и запишите одну мысль для своего бизнеса"
    );
    private static final String DEFAULT_NEXT_STEP = "Откройте материал и начните с первого раздела";
    private static final int REASON_TOPICS = 3;
    /** The order of a list names at most this many kinds of materials. */
    private static final int ORDER_PHRASES = 3;

    /**
     * What put a candidate into the list, strongest first; the order of a list is told by these.
     */
    private enum Kind {
        TOPIC("материалы, которые ближе всего к вашей задаче"),
        TAKEN_AFTER("то, что предприниматели берут после ваших материалов"),
        SIMILAR("материалы на темы, которые вы уже отметили"),
        STAGE("материалы для вашего этапа бизнеса"),
        POPULAR("материалы, популярные у других предпринимателей"),
        CATALOG("материалы на другие темы из каталога");

        private final String phrase;

        Kind(String phrase) {
            this.phrase = phrase;
        }
    }

    private Explanations() {
    }

    static String reason(CandidateServiceDto candidate, ProfileServiceDto profile) {
        return switch (kind(candidate)) {
            case TOPIC -> {
                List<String> topics = candidate.material().topics();
                yield topics.isEmpty()
                        ? "Совпадает с темой вашей задачи"
                        : "Совпадает с вашей задачей по темам: "
                                + String.join(", ", topics.subList(0, Math.min(REASON_TOPICS, topics.size())));
            }
            case TAKEN_AFTER -> "Его часто выбирают после «" + candidate.takenAfter() + "»";
            case SIMILAR -> similarReason(candidate.similarTo());
            case STAGE -> "Подходит " + STAGES.getOrDefault(profile.businessStage(), "вашему этапу бизнеса");
            case POPULAR -> "Популярен у других предпринимателей";
            case CATALOG -> "Подобран из каталога для предпринимателей";
        };
    }

    static String nextStep(MaterialServiceDto material) {
        return material.format() == null ? DEFAULT_NEXT_STEP : NEXT_STEPS.getOrDefault(material.format(), DEFAULT_NEXT_STEP);
    }

    /**
     * Why the list goes in this order, told by what put its items there; {@code null} for an empty list.
     */
    static String order(List<CandidateServiceDto> items, ProfileServiceDto profile) {
        if (items.isEmpty()) {
            return null;
        }

        Set<Kind> kinds = new LinkedHashSet<>();
        items.forEach(item -> kinds.add(kind(item)));
        boolean emptyProfile = !StringUtils.hasText(profile.goal()) && !StringUtils.hasText(profile.businessSphere())
                && profile.businessStage() == null;
        if (emptyProfile && kinds.stream().allMatch(kind -> kind == Kind.POPULAR || kind == Kind.CATALOG)) {
            return "Это стартовая подборка: курсы, популярные у предпринимателей, и короткие материалы на разные темы, "
                    + "чтобы быстро получить первый результат.";
        }

        List<String> phrases = new ArrayList<>();
        kinds.forEach(kind -> phrases.add(kind.phrase));
        if (phrases.size() == 1) {
            return "В подборке — " + phrases.getFirst() + ": самые подходящие идут первыми.";
        }
        return "Сначала — " + phrases.getFirst() + ", затем — "
                + String.join(", ", phrases.subList(1, Math.min(ORDER_PHRASES, phrases.size()))) + ".";
    }

    private static Kind kind(CandidateServiceDto candidate) {
        if (candidate.topicMatch() > 0) {
            return Kind.TOPIC;
        }
        if (candidate.takenAfter() != null) {
            return Kind.TAKEN_AFTER;
        }
        if (candidate.similarTo() != null) {
            return Kind.SIMILAR;
        }
        if (candidate.stageMatch()) {
            return Kind.STAGE;
        }
        return candidate.popularUsers() > 0 ? Kind.POPULAR : Kind.CATALOG;
    }

    private static String similarReason(HistorySource source) {
        return switch (source.event()) {
            case "LIKE" -> "Похож на материал «" + source.title() + "», который вам понравился";
            case "COMPLETE" -> "Продолжает тему материала «" + source.title() + "», который вы изучили";
            default -> "На ту же тему, что и материал «" + source.title() + "», который вы начали";
        };
    }
}
