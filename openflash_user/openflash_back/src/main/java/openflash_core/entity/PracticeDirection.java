package openflash_core.entity;

import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;

/**
 * 统一管理双向练习的方向常量与转换。
 */
public final class PracticeDirection {


    public static final String A_TO_B = "A_TO_B";
    public static final String B_TO_A = "B_TO_A";
    public static final String API_A_TO_B = "a2b";
    public static final String API_B_TO_A = "b2a";

    private PracticeDirection() {
    }

    public static String normalizeStorageDirection(String direction) {
        if (A_TO_B.equals(direction) || API_A_TO_B.equalsIgnoreCase(direction)) {
            return A_TO_B;
        }
        if (B_TO_A.equals(direction) || API_B_TO_A.equalsIgnoreCase(direction)) {
            return B_TO_A;
        }
        throw new AppException(ErrorCode.PRACTICE_DIRECTION_INVALID);
    }

    public static String toApiDirection(String direction) {
        return switch (normalizeStorageDirection(direction)) {
            case A_TO_B -> API_A_TO_B;
            case B_TO_A -> API_B_TO_A;
            default -> throw new IllegalArgumentException("练习方向不合法");
        };
    }
}
