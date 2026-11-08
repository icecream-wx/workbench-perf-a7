package __PACKAGE__.controller;

import __PACKAGE__.domain.Member;
import __PACKAGE__.domain.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String memberNo,
        String name,
        String gender,
        String phone,
        String email,
        String department,
        String position,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate joinDate,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getMemberNo(),
                member.getName(),
                (member.getGender() == null ? Gender.UNKNOWN : member.getGender()).name(),
                member.getPhone(),
                member.getEmail(),
                member.getDepartment(),
                member.getPosition(),
                member.getJoinDate(),
                member.getCreatedAt(),
                member.getUpdatedAt());
    }
}
