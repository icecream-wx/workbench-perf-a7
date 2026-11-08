package __PACKAGE__.repository;

import __PACKAGE__.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByMemberNoIgnoreCase(String memberNo);

    boolean existsByMemberNoIgnoreCaseAndIdNot(String memberNo, Long id);

    @Query("""
            select e from Member e
            where lower(e.memberNo) like lower(concat('%', :keyword, '%'))
               or lower(e.name) like lower(concat('%', :keyword, '%'))
            """)
    Page<Member> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
