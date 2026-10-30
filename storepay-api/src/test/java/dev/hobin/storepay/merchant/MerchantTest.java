package dev.hobin.storepay.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.domain.Merchant;
import dev.hobin.storepay.merchant.domain.MerchantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("가맹점")
class MerchantTest {

    @Test
    @DisplayName("등록 직후에는 PENDING 상태이고 결제를 받을 수 없다")
    void startsAsPending() {
        Merchant merchant = Merchant.register("호빈상사", "123-45-67890");

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getMerchantKey()).startsWith("mch_");
        assertThatThrownBy(merchant::requireActive)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MERCHANT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("정지된 가맹점은 결제를 받을 수 없다")
    void suspendedCannotAcceptPayment() {
        Merchant merchant = Merchant.register("호빈상사", "123-45-67890");
        merchant.activate();
        merchant.suspend();

        assertThatThrownBy(merchant::requireActive).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("해지된 가맹점은 다시 활성화할 수 없다")
    void terminatedCannotBeReactivated() {
        Merchant merchant = Merchant.register("호빈상사", "123-45-67890");
        ReflectionTestUtils.setField(merchant, "status", MerchantStatus.TERMINATED);

        assertThatThrownBy(merchant::activate)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("해지된 가맹점");
    }
}
