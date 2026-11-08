package com.example.perfa7.repository;

import com.example.perfa7.domain.Employee;
import com.example.perfa7.domain.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeNoIgnoreCase(String employeeNo);

    boolean existsByEmployeeNoIgnoreCaseAndIdNot(String employeeNo, Long id);

    @Query("""
            select e from Employee e
            where (:keyword is null
                   or lower(e.employeeNo) like lower(concat('%', :keyword, '%'))
                   or lower(e.name) like lower(concat('%', :keyword, '%')))
              and (:department is null or e.department = :department)
              and (:status is null or e.status = :status)
            """)
    Page<Employee> search(@Param("keyword") String keyword,
                          @Param("department") String department,
                          @Param("status") EmployeeStatus status,
                          Pageable pageable);
}
