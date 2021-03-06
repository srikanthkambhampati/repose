/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.core.services.datastore.impl.ehcache;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.apache.commons.lang3.SerializationUtils;
import org.openrepose.core.services.datastore.Datastore;
import org.openrepose.core.services.datastore.DatastoreOperationException;
import org.openrepose.core.services.datastore.Patch;
import org.openrepose.core.services.datastore.Patchable;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class EHCacheDatastore implements Datastore {

    private static final String NAME = "local/default";
    private final Ehcache ehCacheInstance;

    public EHCacheDatastore(Ehcache ehCacheInstance) {
        this.ehCacheInstance = ehCacheInstance;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean remove(String key) {
        return ehCacheInstance.remove(key);
    }

    @Override
    public Serializable get(String key) {
        Element element = ehCacheInstance.get(key);
        if (element != null) {
            return element.getValue();
        } else {
            return null;
        }
    }

    @Override
    public void put(String key, Serializable value) {
        ehCacheInstance.put(new Element(key, value));
    }

    @Override
    public void put(String key, Serializable value, int ttl, TimeUnit timeUnit) {
        Element putMe = new Element(key, value);
        putMe.setTimeToLive((int) TimeUnit.SECONDS.convert(ttl, timeUnit));
        //todo: switch to time to idle instead of time to live?

        ehCacheInstance.put(putMe);
    }

    @Override
    public Serializable patch(String key, Patch patch) throws DatastoreOperationException {
        return patch(key, patch, -1, TimeUnit.MINUTES);
    }

    @Override
    public Serializable patch(String key, Patch patch, int ttl, TimeUnit timeUnit) throws DatastoreOperationException {
        Serializable potentialNewValue = (Serializable) patch.newFromPatch();
        Element element = new Element(key, potentialNewValue);
        Element currentElement = ehCacheInstance.putIfAbsent(element);
        Serializable returnValue;

        if (currentElement == null) {
            returnValue = SerializationUtils.clone(potentialNewValue);
            currentElement = element;
        } else {
            returnValue = (Serializable) ((Patchable) currentElement.getValue()).applyPatch(patch);
        }

        //todo: setting ttl can die once we move to tti
        if (ttl == 0) {
            currentElement.setTimeToLive(0);
            currentElement.setTimeToIdle(0);
        } else if (ttl > 0) {
            int convertedTtl = (int) TimeUnit.SECONDS.convert(ttl, timeUnit);
            int currentLifeSpan = (int) TimeUnit.SECONDS.convert(System.currentTimeMillis() - currentElement.getCreationTime(), TimeUnit.MILLISECONDS);
            if ((currentLifeSpan + convertedTtl) > currentElement.getTimeToLive()) {
                currentElement.setTimeToLive(currentLifeSpan + convertedTtl);
            }
            if (convertedTtl > currentElement.getTimeToIdle()) {
                currentElement.setTimeToIdle(convertedTtl);
            }
        }

        return returnValue;
    }

    @Override
    public void removeAll() {
        ehCacheInstance.removeAll();
    }
}
