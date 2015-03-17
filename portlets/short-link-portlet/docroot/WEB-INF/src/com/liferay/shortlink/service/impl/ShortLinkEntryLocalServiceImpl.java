/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.shortlink.service.impl;

import com.liferay.portal.kernel.dao.orm.ORMException;
import com.liferay.portal.kernel.dao.orm.QueryPos;
import com.liferay.portal.kernel.dao.orm.SQLQuery;
import com.liferay.portal.kernel.dao.orm.Session;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.transaction.Isolation;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.Transactional;
import com.liferay.portal.kernel.util.CalendarUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.shortlink.DuplicateShortLinkEntryException;
import com.liferay.shortlink.ShortLinkEntryOriginalURLException;
import com.liferay.shortlink.ShortLinkEntryShortURLException;
import com.liferay.shortlink.model.ShortLinkEntry;
import com.liferay.shortlink.service.base.ShortLinkEntryLocalServiceBaseImpl;
import com.liferay.shortlink.util.ShortLinkConstants;
import com.liferay.shortlink.util.ShortURLUtil;
import com.liferay.util.dao.orm.CustomSQLUtil;

import java.sql.Timestamp;

import java.util.Date;
import java.util.List;

/**
 * @author Miroslav Ligas
 */
public class ShortLinkEntryLocalServiceImpl
	extends ShortLinkEntryLocalServiceBaseImpl {

	public ShortLinkEntry addShortLinkEntry(
			String originalURL, String shortURL, boolean autogenerated)
		throws PortalException, SystemException {

		Date now = new Date();

		validate(0, originalURL, shortURL, autogenerated);

		long shortLinkEntryId = counterLocalService.increment(
			ShortLinkEntry.class.getName());

		ShortLinkEntry shortLinkEntry = shortLinkEntryPersistence.create(
			shortLinkEntryId);

		shortLinkEntry.setCreateDate(now);
		shortLinkEntry.setModifiedDate(now);
		shortLinkEntry.setOriginalURL(originalURL);

		if (autogenerated) {
			shortURL =
				ShortLinkConstants.AUTOGENERATED_PREFIX +
					ShortURLUtil.encode(shortLinkEntryId);
		}

		shortLinkEntry.setShortURL(shortURL);

		shortLinkEntry.setAutogenerated(autogenerated);
		shortLinkEntry.setActive(true);

		return shortLinkEntryPersistence.update(shortLinkEntry, false);
	}

	@Override
	@Transactional(
		isolation = Isolation.READ_COMMITTED,
		propagation = Propagation.REQUIRES_NEW)
	public void deleteShortLinkEntries(Date modifiedDate) {
		try {
			Session session = shortLinkEntryPersistence.openSession();

			String sql = CustomSQLUtil.get(_DELETE_SHORT_LINK_ENTRIES);

			SQLQuery sqlQuery = session.createSQLQuery(sql);

			QueryPos qPos = QueryPos.getInstance(sqlQuery);

			Timestamp modifiedDateTS = CalendarUtil.getTimestamp(modifiedDate);

			qPos.add(modifiedDateTS);

			sqlQuery.executeUpdate();

			shortLinkEntryPersistence.closeSession(session);
		}
		catch (ORMException orme) {
			_log.error("Unable to remove old short links.", orme);
		}
	}

	@Override
	public List<ShortLinkEntry> getShortLinkEntries(
			boolean autogenerated, int start, int end)
		throws SystemException {

		return shortLinkEntryPersistence.findByAutogenerated(
			autogenerated, start, end);
	}

	@Override
	public ShortLinkEntry getShortLinkEntry(
			String shortURL, boolean autogenerated)
		throws PortalException, SystemException {

		return shortLinkEntryPersistence.findBySURL_A(shortURL, autogenerated);
	}

	public ShortLinkEntry updateShortLinkEntry(
			long shortLinkEntryId, String originalURL, String shortURL,
			boolean active)
		throws PortalException, SystemException {

		ShortLinkEntry shortLinkEntry =
			shortLinkEntryPersistence.findByPrimaryKey(shortLinkEntryId);

		validate(
			shortLinkEntryId, originalURL, shortURL,
			shortLinkEntry.getAutogenerated());

		shortLinkEntry.setModifiedDate(new Date());
		shortLinkEntry.setOriginalURL(originalURL);

		if (!shortLinkEntry.isAutogenerated()) {
			shortLinkEntry.setShortURL(shortURL);
		}

		shortLinkEntry.setActive(active);

		return shortLinkEntryPersistence.update(shortLinkEntry, false);
	}

	protected void validate(
			long shortLinkEntryId, String originalURL, String shortURL,
			boolean autogenerated)
		throws PortalException, SystemException {

		if (Validator.isNull(originalURL)) {
			throw new ShortLinkEntryOriginalURLException();
		}

		if (!autogenerated && Validator.isNull(shortURL)) {
			throw new ShortLinkEntryShortURLException();
		}

		ShortLinkEntry shortLinkEntry =
			shortLinkEntryPersistence.fetchByShortURL(shortURL);

		if ((shortLinkEntry != null) &&
			(shortLinkEntry.getShortLinkEntryId() != shortLinkEntryId)) {

			throw new DuplicateShortLinkEntryException();
		}
	}

	private static final String _DELETE_SHORT_LINK_ENTRIES =
		ShortLinkEntryLocalServiceImpl.class.getName() +
			".deleteShortLinkEntries";

	private static Log _log = LogFactoryUtil.getLog(
		ShortLinkEntryLocalServiceImpl.class);

}