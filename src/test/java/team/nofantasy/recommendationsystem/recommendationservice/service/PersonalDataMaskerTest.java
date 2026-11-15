package team.nofantasy.recommendationsystem.recommendationservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataMaskerTest {
    @Test
    void hidesEmailsAndPhoneNumbers() {
        assertThat(PersonalDataMasker.mask("Пишите на ivan.petrov@mail.ru или звоните +7 (999) 123-45-67"))
                .isEqualTo("Пишите на [email] или звоните [phone]");
        assertThat(PersonalDataMasker.mask("8 999 123 45 67, 89991234567"))
                .isEqualTo("[phone], [phone]");
    }

    @Test
    void keepsBusinessNumbers() {
        String goal = "Выручка 10 000 000 рублей в год к 2027 году, 25 сотрудников";

        assertThat(PersonalDataMasker.mask(goal)).isEqualTo(goal);
    }

    @Test
    void keepsNull() {
        assertThat(PersonalDataMasker.mask(null)).isNull();
    }
}
