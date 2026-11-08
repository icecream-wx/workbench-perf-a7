package __PACKAGE__.service;

import __PACKAGE__.controller.MemberRequest;
import __PACKAGE__.domain.Member;
import __PACKAGE__.domain.Gender;
import __PACKAGE__.exception.BizException;
import __PACKAGE__.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Service
public class MemberService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Page<Member> list(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        String condition = keyword == null ? "" : keyword.trim();
        if (condition.isEmpty()) {
            return memberRepository.findAll(pageable);
        }
        return memberRepository.searchByKeyword(condition, pageable);
    }

    @Transactional(readOnly = true)
    public Member get(long id) {
        return memberRepository.findById(id).orElseThrow(() -> new BizException(40401, "成员不存在"));
    }

    @Transactional
    public Member create(MemberRequest request) {
        String memberNo = request.getMemberNo();
        if (memberRepository.existsByMemberNoIgnoreCase(memberNo)) {
            throw new BizException(40901, "成员编号已存在");
        }
        Member member = new Member();
        applyEditableFields(member, request);
        LocalDateTime now = now();
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        return memberRepository.save(member);
    }

    @Transactional
    public Member update(long id, MemberRequest request) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new BizException(40401, "成员不存在"));
        String memberNo = request.getMemberNo();
        if (memberRepository.existsByMemberNoIgnoreCaseAndIdNot(memberNo, id)) {
            throw new BizException(40901, "成员编号已存在");
        }
        applyEditableFields(member, request);
        LocalDateTime now = now();
        if (!now.isAfter(member.getUpdatedAt())) {
            now = member.getUpdatedAt().plusSeconds(1);
        }
        member.setUpdatedAt(now);
        return memberRepository.save(member);
    }

    private void applyEditableFields(Member member, MemberRequest request) {
        member.setMemberNo(request.getMemberNo());
        member.setName(request.getName());
        Gender gender = Gender.fromCode(request.getGender());
        if (gender == null) {
            throw new BizException(400, "性别取值不合法");
        }
        member.setGender(gender);
        member.setPhone(request.getPhone());
        member.setEmail(request.getEmail());
        member.setDepartment(request.getDepartment());
        member.setPosition(request.getPosition());
        member.setJoinDate(parseJoinDate(request.getJoinDate()));
    }

    private LocalDate parseJoinDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BizException(400, "加入日期格式不正确");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
    }
}
