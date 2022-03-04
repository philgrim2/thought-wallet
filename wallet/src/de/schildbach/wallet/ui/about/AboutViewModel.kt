/*
 * Copyright 2022 Dash Core Group.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.about

import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.dash.wallet.common.services.analytics.AnalyticsService
import org.dash.wallet.features.exploredash.data.AtmDao
import org.dash.wallet.features.exploredash.data.MerchantDao
import org.dash.wallet.features.exploredash.repository.ExploreRepository
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val analytics: AnalyticsService,
    private val exploreRepository: ExploreRepository,
    private val atmDao: AtmDao,
    private val merchantDao: MerchantDao
): ViewModel() {

    private val _exploreRemoteTimestamp = MutableLiveData<Long>()
    val exploreRemoteTimestamp: LiveData<Long>
        get() = _exploreRemoteTimestamp

    private val _exploreDataCount = MutableLiveData<Pair<Int, Int>>()
    val exploreDataCount: LiveData<Pair<Int, Int>>
        get() = _exploreDataCount

    val exploreLastSync: Long
        get() = exploreRepository.localTimestamp

    init {
        viewModelScope.launch {
            _exploreRemoteTimestamp.value = exploreRepository.getRemoteTimestamp()
            val merchantCount = merchantDao.getCount()
            val atmCount = atmDao.getCount()
            _exploreDataCount.value = Pair(merchantCount, atmCount)
        }
    }

    fun logEvent(event: String) {
        analytics.logEvent(event, bundleOf())
    }
}