package openflash_core.entity;

/**
 * 按 A->B / B->A 暴露卡片双方向进度。
 */
public class CardDirectionProgresses {

    private DirectionProgressSnapshot a2b;
    private DirectionProgressSnapshot b2a;

    public DirectionProgressSnapshot getA2b() {
        return a2b;
    }

    public void setA2b(DirectionProgressSnapshot a2b) {
        this.a2b = a2b;
    }

    public DirectionProgressSnapshot getB2a() {
        return b2a;
    }

    public void setB2a(DirectionProgressSnapshot b2a) {
        this.b2a = b2a;
    }
}
