/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 **/
package org.codice.ddf.registry.federationadmin.service.impl;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.registry.api.RegistryStore;
import org.codice.ddf.registry.common.metacard.RegistryObjectMetacardType;
import org.codice.ddf.registry.common.metacard.RegistryUtility;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.codice.ddf.registry.federationadmin.service.RegistryPublicationService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;

public class RegistryPublicationServiceImpl implements RegistryPublicationService {

    private static final String NO_PUBLICATIONS = "No_Publications";

    private FederationAdminService federationAdminService;

    private List<RegistryStore> registryStores = new ArrayList<>();

    @Override
    public void publish(String registryId, String destinationRegistryId)
            throws FederationAdminException {
        Metacard metacard = getMetacard(registryId);
        String metacardId = metacard.getId();

        List<String> locations = RegistryUtility.getListOfStringAttribute(metacard,
                RegistryObjectMetacardType.PUBLISHED_LOCATIONS);

        if (locations.contains(destinationRegistryId)) {
            return;
        }

        String sourceId = getSourceIdFromRegistryId(destinationRegistryId);
        if (sourceId == null) {
            throw new FederationAdminException(
                    "Could not find a source id for registry-id " + destinationRegistryId);
        }

        federationAdminService.addRegistryEntry(metacard, Collections.singleton(sourceId));

        //need to reset the id since the framework reset it in the groomer plugin
        //and we don't want to update with the wrong id
        metacard.setAttribute(new AttributeImpl(Metacard.ID, metacardId));

        locations.add(destinationRegistryId);
        locations.remove(NO_PUBLICATIONS);

        ArrayList<String> locArr = new ArrayList<>(locations);

        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                locArr));
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.LAST_PUBLISHED,
                Date.from(ZonedDateTime.now()
                        .toInstant())));

        federationAdminService.updateRegistryEntry(metacard);

    }

    @Override
    public void unpublish(String registryId, String destinationRegistryId)
            throws FederationAdminException {
        Metacard metacard = getMetacard(registryId);

        List<String> locations = RegistryUtility.getListOfStringAttribute(metacard,
                RegistryObjectMetacardType.PUBLISHED_LOCATIONS);
        if (!locations.contains(destinationRegistryId)) {
            return;
        }

        locations.remove(destinationRegistryId);
        if (locations.isEmpty()) {
            locations.add(NO_PUBLICATIONS);
        }

        ArrayList<String> locArr = new ArrayList<>();
        locArr.addAll(locations);

        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.PUBLISHED_LOCATIONS,
                locArr));
        metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.LAST_PUBLISHED,
                Date.from(ZonedDateTime.now()
                        .toInstant())));

        federationAdminService.updateRegistryEntry(metacard);

        String sourceId = getSourceIdFromRegistryId(destinationRegistryId);

        if (sourceId == null) {
            throw new FederationAdminException(
                    "Could not find a source id for registry-id " + destinationRegistryId);
        }

        federationAdminService.deleteRegistryEntriesByRegistryIds(Collections.singletonList(
                registryId), Collections.singleton(sourceId));

    }

    @Override
    public void update(Metacard metacard) throws FederationAdminException {

        List<String> publishedLocations = RegistryUtility.getListOfStringAttribute(metacard,
                RegistryObjectMetacardType.PUBLISHED_LOCATIONS);

        if (publishedLocations.isEmpty()) {
            return;
        }

        Set<String> locations = publishedLocations.stream()
                .map(registryId -> getSourceIdFromRegistryId(registryId))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        if (CollectionUtils.isNotEmpty(locations)) {
            federationAdminService.updateRegistryEntry(metacard, locations);
            metacard.setAttribute(new AttributeImpl(RegistryObjectMetacardType.LAST_PUBLISHED,
                    Date.from(ZonedDateTime.now()
                            .toInstant())));
            federationAdminService.updateRegistryEntry(metacard);
        }
    }

    public void bindRegistryStore(ServiceReference serviceReference) {
        BundleContext bundleContext = getBundleContext();

        if (serviceReference != null && bundleContext != null) {
            RegistryStore registryStore =
                    (RegistryStore) bundleContext.getService(serviceReference);

            registryStores.add(registryStore);
        }
    }

    public void unbindRegistryStore(ServiceReference serviceReference) {
        BundleContext bundleContext = getBundleContext();

        if (serviceReference != null && bundleContext != null) {
            RegistryStore registryStore =
                    (RegistryStore) bundleContext.getService(serviceReference);

            registryStores.remove(registryStore);
        }
    }

    BundleContext getBundleContext() {
        Bundle bundle = FrameworkUtil.getBundle(this.getClass());
        if (bundle != null) {
            return bundle.getBundleContext();
        }
        return null;
    }

    private Metacard getMetacard(String registryId) throws FederationAdminException {
        List<Metacard> metacards = federationAdminService.getRegistryMetacardsByRegistryIds(
                Collections.singletonList(registryId));

        if (CollectionUtils.isEmpty(metacards)) {
            throw new FederationAdminException(
                    "Could not retrieve metacard with registry-id " + registryId);
        }

        return metacards.get(0);
    }

    public void setFederationAdminService(FederationAdminService federationAdminService) {
        this.federationAdminService = federationAdminService;
    }

    private String getSourceIdFromRegistryId(String registryId) {

        for (RegistryStore registryStore : registryStores) {
            if (registryStore.getRegistryId()
                    .equals(registryId)) {
                if (StringUtils.isNotBlank(registryStore.getId())) {
                    return registryStore.getId();
                }
            }
        }

        return null;
    }

}
