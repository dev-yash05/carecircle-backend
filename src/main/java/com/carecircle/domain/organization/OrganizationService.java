package com.carecircle.domain.organization;

import com.carecircle.domain.organization.dto.OrganizationDto;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public OrganizationDto.Response createOrganization(OrganizationDto.CreateRequest request) {
        Organization org = new Organization();
        org.setName(request.getName());
        org.setPlanType(Organization.PlanType.valueOf(request.getPlanType()));
        Organization saved = organizationRepository.save(org);
        log.info("Organization created: {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationDto.Response getOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        return toResponse(org);
    }

    private OrganizationDto.Response toResponse(Organization org) {
        return OrganizationDto.Response.builder()
                .id(org.getId())
                .name(org.getName())
                .planType(org.getPlanType().name())
                .createdAt(org.getCreatedAt())
                .build();
    }
}