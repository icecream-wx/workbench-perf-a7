package __PACKAGE__.controller;

import java.util.List;

public record MemberPageResponse(List<MemberResponse> items, long total) {
}
