package edu.trk.start_project;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

// JpaRepository дає save/findById/findAll/delete/count готовими.
// JpaSpecificationExecutor дозволяє складати фільтри динамічно (пошук, підрозділ, статус)
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    // Скільки людей у кожному статусі: рядки виду [статус, кількість]
    @Query("select p.status, count(p) from Person p group by p.status")
    List<Object[]> countByStatus();

    // Скільки людей у кожному підрозділі (без порожніх)
    @Query("select p.unit, count(p) from Person p where p.unit is not null group by p.unit order by p.unit")
    List<Object[]> countByUnit();
}