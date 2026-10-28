package com.hrm.controller;

import java.security.Principal;
import com.hrm.dto.*;
import com.hrm.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/qualification-levels")
public class QualificationLevelController {
    private final QualificationLevelService service;
    private final PersonnelAccessService access;
    private final PersonnelCatalogService writes;
    public QualificationLevelController(QualificationLevelService service,PersonnelAccessService access,PersonnelCatalogService writes){this.service=service;this.access=access;this.writes=writes;}
    @GetMapping public Page<QualificationLevelDTO> getAllQualificationLevels(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p){access.actor(p);return service.getLevelsPage(page,size);}
    @GetMapping("/{id}") public QualificationLevelDTO getQualificationLevelById(@PathVariable Long id,Principal p){access.actor(p);return service.getQualificationLevelById(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public QualificationLevelDTO addQualificationLevel(@Valid @RequestBody QualificationLevelRequestDTO request,Principal p){return writes.write(p,"qualification_levels",null,"QUALIFICATION_LEVEL_CREATE",()->service.addQualificationLevel(request),QualificationLevelDTO::qualificationLevelId);}
    @PutMapping("/{id}") public QualificationLevelDTO updateQualificationLevel(@PathVariable Long id,@Valid @RequestBody QualificationLevelRequestDTO request,Principal p){return writes.write(p,"qualification_levels",id,"QUALIFICATION_LEVEL_UPDATE",()->service.updateQualificationLevel(id,request),QualificationLevelDTO::qualificationLevelId);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQualificationLevel(@PathVariable Long id,Principal p){writes.write(p,"qualification_levels",id,"QUALIFICATION_LEVEL_DEACTIVATE",()->{service.deleteQualificationLevel(id);return id;},Long::longValue);}
}
