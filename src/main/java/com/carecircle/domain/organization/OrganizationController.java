package com.carecircle.domain.organization;

import com.carecircle.domain.organization.dto.OrganizationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDto.Response createOrganization(
            @Valid @RequestBody OrganizationDto.CreateRequest request) {
        return organizationService.createOrganization(request);
    }

    @GetMapping("/{id}")
    public OrganizationDto.Response getOrganization(@PathVariable UUID id) {
        return organizationService.getOrganization(id);
    }
}