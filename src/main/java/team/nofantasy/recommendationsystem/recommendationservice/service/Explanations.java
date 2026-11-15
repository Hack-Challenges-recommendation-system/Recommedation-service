package team.nofantasy.recommendationsystem.recommendationservice.service;

import team.nofantasy.recommendationsystem.recommendationservice.dto.service.catalog.MaterialServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.profile.ProfileServiceDto;
import team.nofantasy.recommendationsystem.recommendationservice.dto.service.recommendation.CandidateServiceDto;

import java.util.List;
import java.util.Map;

/**
 * Reasons and next steps written from the signals of a candidate: for the baseline, and for gaps in an LLM answer.
 * Every item of a list must say why it was picked and what to do next.
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
            "CHECKLIST", "Пройдите чек-лист и отметьте, что уже сделано"
    );
    private static final String DEFAULT_NEXT_STEP = "Откройте материал и начните с первого раздела";
    private static final int REASON_TOPICS = 3;

    private Explanations() {
    }

    static String reason(CandidateServiceDto candidate, ProfileServiceDto profile) {
        if (candidate.topicMatch() > 0) {
            List<String> topics = candidate.material().topics();
            return topics.isEmpty()
                    ? "Совпадает с темой вашей задачи"
                    : "Совпадает с вашей задачей по темам: "
                            + String.join(", ", topics.subList(0, Math.min(REASON_TOPICS, topics.size())));
        }
        if (candidate.takenAfter() != null) {
            return "Его часто выбирают после «" + candidate.takenAfter() + "»";
        }
        if (candidate.stageMatch()) {
            return "Подходит " + STAGES.getOrDefault(profile.businessStage(), "вашему этапу бизнеса");
        }
        if (candidate.popularUsers() > 0) {
            return "Популярен у других предпринимателей";
        }

        return "Подобран из каталога для предпринимателей";
    }

    static String nextStep(MaterialServiceDto material) {
        return material.format() == null ? DEFAULT_NEXT_STEP : NEXT_STEPS.getOrDefault(material.format(), DEFAULT_NEXT_STEP);
    }
}
