package __PACKAGE__.controller;

import __PACKAGE__.domain.Member;
import __PACKAGE__.exception.ApiResponse;
import __PACKAGE__.exception.BizException;
import __PACKAGE__.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<MemberPageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        if (page < 1) {
            throw new BizException(400, "页码不能小于1");
        }
        if (size < 1) {
            throw new BizException(400, "每页条数不能小于1");
        }
        Page<Member> result = memberService.list(keyword, page, size);
        List<MemberResponse> items = result.getContent().stream()
                .map(MemberResponse::from)
                .toList();
        return ApiResponse.ok(new MemberPageResponse(items, result.getTotalElements()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> detail(@PathVariable long id) {
        return ApiResponse.ok(MemberResponse.from(memberService.get(id)));
    }

    @PostMapping
    public ApiResponse<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        return ApiResponse.ok(MemberResponse.from(memberService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<MemberResponse> update(@PathVariable long id, @Valid @RequestBody MemberRequest request) {
        return ApiResponse.ok(MemberResponse.from(memberService.update(id, request)));
    }
}
