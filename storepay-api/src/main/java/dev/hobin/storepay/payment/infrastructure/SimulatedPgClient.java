package dev.hobin.storepay.payment.infrastructure;

import dev.hobin.storepay.common.support.PublicKeyGenerator;
import dev.hobin.storepay.payment.domain.PgClient;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 외부 결제사 없이 돌리기 위한 구현.
 *
 * <p>테스트와 로컬 실행에서 쓴다. 승인 거절 경로를 테스트하려면 금액이 필요하므로,
 * 금액 끝자리가 {@code .99} 인 요청은 한도 초과로 거절하도록 약속해 뒀다.
 * 운영 프로필에서는 {@link HttpPgClient} 가 대신 등록된다.
 */
@Slf4j
@Component
@Profile("!pg-http")
public class SimulatedPgClient implements PgClient {

    private static final BigDecimal DECLINE_MARKER = new BigDecimal("0.99");

    @Override
    public ApproveResult approve(ApproveCommand command) {
        log.debug("PG 승인 요청 (시뮬레이션): paymentKey={}, amount={}", command.paymentKey(), command.amount());

        if (command.amount().remainder(BigDecimal.ONE).compareTo(DECLINE_MARKER) == 0) {
            return ApproveResult.declined("한도 초과");
        }
        return ApproveResult.approved(PublicKeyGenerator.generate("pgtx"), Instant.now());
    }

    @Override
    public CancelResult cancel(CancelCommand command) {
        log.debug("PG 취소 요청 (시뮬레이션): pgTransactionId={}, amount={}",
                command.pgTransactionId(), command.amount());
        return new CancelResult(PublicKeyGenerator.generate("pgcl"), Instant.now());
    }
}
