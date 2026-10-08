package edu.trk.start_project;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/personnel")   // усі адреси цього класу починаються з /personnel
public class PersonnelController {

    private static final List<String> STATUSES = List.of("present", "leave", "sick", "mission", "duty");

    // Обгортка для списку: записи + загальна кількість + номер сторінки
    public record PageResponse<T>(List<T> items, long total, int page, int size) {
    }

    private final PersonRepository repo;

    // Spring сам передає репозиторій у конструктор
    public PersonnelController(PersonRepository repo) {
        this.repo = repo;
    }

    // GET /personnel?search=Іван&unit=1 рота&status=sick&page=0&size=20
    @GetMapping
    public PageResponse<Person> list(@RequestParam(required = false) String search,
                                     @RequestParam(required = false) String unit,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);   // не більше 100 за раз

        // Починаємо з "без умов" і додаємо тільки ті фільтри, що передані
        Specification<Person> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.<String>get("fullName")), like));
        }
        if (StringUtils.hasText(unit)) {
            String u = unit.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("unit"), u));
        }
        if (StringUtils.hasText(status)) {
            String s = status.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), s));
        }

        Page<Person> result = repo.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by("fullName")));
        return new PageResponse<>(result.getContent(), result.getTotalElements(), safePage, safeSize);
    }

    // GET /personnel/summary: цифри для зведення
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : STATUSES) {
            byStatus.put(s, 0L);                       // щоб нулі теж були у відповіді
        }
        for (Object[] row : repo.countByStatus()) {
            byStatus.put((String) row[0], (Long) row[1]);
        }
        Map<String, Long> byUnit = new LinkedHashMap<>();
        for (Object[] row : repo.countByUnit()) {
            byUnit.put((String) row[0], (Long) row[1]);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", repo.count());
        body.put("by_status", byStatus);
        body.put("by_unit", byUnit);
        return body;
    }

    @GetMapping("/{id}")
    public Person get(@PathVariable Long id) {
        return find(id);
    }

    // POST /personnel: створити. @Valid вмикає перевірки з PersonRequest
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)               // 201 замість 200
    public Person create(@Valid @RequestBody PersonRequest req) {
        Person p = new Person();
        apply(p, req);
        return repo.save(p);
    }

    // PUT /personnel/5: замінити дані запису
    @PutMapping("/{id}")
    public Person update(@PathVariable Long id, @Valid @RequestBody PersonRequest req) {
        Person p = find(id);
        String oldStatus = p.getStatus();
        apply(p, req);
        if (!Objects.equals(oldStatus, p.getStatus())) {
            p.setStatusChangedAt(Instant.now());      // запам'ятовуємо, коли змінився статус
        }
        return repo.save(p);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)            // 204: успіх без тіла
    public void delete(@PathVariable Long id) {
        repo.delete(find(id));
    }

    private Person find(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Person " + id + " not found"));
    }

    // Переносить поля із запиту в сутність
    private void apply(Person p, PersonRequest req) {
        p.setFullName(req.fullName().trim());
        p.setRank(req.rank());
        p.setPosition(req.position());
        p.setUnit(req.unit());
        p.setNote(req.note());
        if (req.status() != null) {
            p.setStatus(req.status());
        }
    }
}