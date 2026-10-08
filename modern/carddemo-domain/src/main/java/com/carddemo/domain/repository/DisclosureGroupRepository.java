package com.carddemo.domain.repository;

import com.carddemo.domain.model.DisclosureGroup;
import com.carddemo.domain.model.DisclosureGroupId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclosureGroupRepository extends JpaRepository<DisclosureGroup, DisclosureGroupId> {
}
