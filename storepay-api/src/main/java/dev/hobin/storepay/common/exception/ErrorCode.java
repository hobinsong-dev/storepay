package dev.hobin.storepay.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_REQUEST("COMMON_400", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND("COMMON_404", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR("COMMON_500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 가맹점
    MERCHANT_NOT_FOUND("MERCHANT_404", HttpStatus.NOT_FOUND, "가맹점을 찾을 수 없습니다."),
    MERCHANT_NOT_ACTIVE("MERCHANT_409", HttpStatus.CONFLICT, "활성 상태가 아닌 가맹점입니다."),
    MERCHANT_BUSINESS_NUMBER_DUPLICATED("MERCHANT_409_1", HttpStatus.CONFLICT, "이미 등록된 사업자등록번호입니다."),

    // 매장
    STORE_NOT_FOUND("STORE_404", HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
    STORE_NOT_ACTIVE("STORE_409", HttpStatus.CONFLICT, "영업 중이 아닌 매장입니다."),

    // 상품
    PRODUCT_NOT_FOUND("PRODUCT_404", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_SELLABLE("PRODUCT_409", HttpStatus.CONFLICT, "판매 중이 아닌 상품입니다."),

    // 주문
    ORDER_NOT_FOUND("ORDER_404", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_EMPTY_ITEMS("ORDER_400", HttpStatus.BAD_REQUEST, "주문 항목이 비어 있습니다."),
    ORDER_ITEM_STORE_MISMATCH("ORDER_400_1", HttpStatus.BAD_REQUEST, "다른 매장의 상품은 같은 주문에 담을 수 없습니다."),
    ORDER_INVALID_STATUS("ORDER_409", HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),

    // 결제
    PAYMENT_NOT_FOUND("PAYMENT_404", HttpStatus.NOT_FOUND, "결제 건을 찾을 수 없습니다."),
    PAYMENT_INVALID_STATUS("PAYMENT_409", HttpStatus.CONFLICT, "현재 결제 상태에서는 처리할 수 없습니다."),
    PAYMENT_INVALID_CANCEL_AMOUNT("PAYMENT_400", HttpStatus.BAD_REQUEST, "취소 금액이 올바르지 않습니다."),
    PAYMENT_ALREADY_EXISTS("PAYMENT_409_1", HttpStatus.CONFLICT, "이미 결제가 진행된 주문입니다."),
    PAYMENT_PG_FAILED("PAYMENT_502", HttpStatus.BAD_GATEWAY, "결제사 승인에 실패했습니다."),
    PAYMENT_PG_UNAVAILABLE("PAYMENT_504", HttpStatus.GATEWAY_TIMEOUT, "결제사와 통신할 수 없습니다."),

    // 멱등성
    IDEMPOTENCY_KEY_REQUIRED("IDEMPOTENCY_400", HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_CONFLICT("IDEMPOTENCY_409", HttpStatus.CONFLICT,
            "같은 Idempotency-Key 로 다른 내용의 요청이 들어왔습니다."),
    IDEMPOTENCY_IN_PROGRESS("IDEMPOTENCY_409_1", HttpStatus.CONFLICT, "같은 요청이 처리 중입니다."),

    // 웹훅
    WEBHOOK_INVALID_SIGNATURE("WEBHOOK_401", HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다."),
    WEBHOOK_STALE_TIMESTAMP("WEBHOOK_401_1", HttpStatus.UNAUTHORIZED, "웹훅 타임스탬프가 허용 범위를 벗어났습니다."),
    WEBHOOK_PAYMENT_NOT_READY("WEBHOOK_409", HttpStatus.CONFLICT,
            "아직 알 수 없는 결제 건입니다. 잠시 후 재전송해 주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
