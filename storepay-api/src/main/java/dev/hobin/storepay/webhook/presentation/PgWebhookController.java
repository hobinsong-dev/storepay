package dev.hobin.storepay.webhook.presentation;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.webhook.application.PgWebhookService;
import dev.hobin.storepay.webhook.application.WebhookSignatureVerifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "웹훅", description = "결제사 비동기 통지 수신")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks")
public class PgWebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final PgWebhookService webhookService;

    /**
     * 본문을 {@code String} 으로 받는다. DTO 로 역직렬화한 뒤 다시 직렬화해서 서명을 계산하면
     * 필드 순서나 공백이 달라져 서명이 깨진다. 서명 검증은 받은 바이트 그대로에 해야 한다.
     */
    @Operation(
            summary = "결제사 웹훅 수신",
            description = """
                    HMAC-SHA256 서명과 타임스탬프를 검증한다.
                    같은 이벤트 ID 가 다시 오면 무시한다(at-least-once 대응).""")
    @PostMapping("/pg")
    public ApiResponse<WebhookResult> receive(
            @RequestHeader("X-Event-Id") String eventId,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Signature") String signature,
            @RequestBody String rawBody) {

        signatureVerifier.verify(signature, timestamp, rawBody);
        PgWebhookService.Result result = webhookService.handle(eventId, rawBody);
        return ApiResponse.ok(new WebhookResult(result.name()));
    }

    public record WebhookResult(String result) {
    }
}
