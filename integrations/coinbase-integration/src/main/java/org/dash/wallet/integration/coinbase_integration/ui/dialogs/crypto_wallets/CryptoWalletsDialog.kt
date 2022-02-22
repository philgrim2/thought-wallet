/*
 * Copyright 2021 Dash Core Group.
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
package org.dash.wallet.integration.coinbase_integration.ui.dialogs.crypto_wallets

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bitcoinj.core.Coin
import org.bitcoinj.utils.MonetaryFormat
import org.dash.wallet.common.R
import org.dash.wallet.common.data.ExchangeRate
import org.dash.wallet.common.databinding.DialogOptionPickerBinding
import org.dash.wallet.common.ui.ListDividerDecorator
import org.dash.wallet.common.ui.dialogs.OffsetDialogFragment
import org.dash.wallet.common.ui.radio_group.IconSelectMode
import org.dash.wallet.common.ui.radio_group.IconifiedViewItem
import org.dash.wallet.common.ui.radio_group.RadioGroupAdapter
import org.dash.wallet.common.ui.viewBinding
import org.dash.wallet.common.util.GenericUtils
import org.dash.wallet.integration.coinbase_integration.model.CoinBaseUserAccountDataUIModel
import org.dash.wallet.integration.coinbase_integration.model.getCoinBaseExchangeRateConversion

@AndroidEntryPoint
class CryptoWalletsDialog(
    private val userAccountsWithBalance: List<CoinBaseUserAccountDataUIModel>,
    private val selectedCurrencyCode: String = "USD",
    private val clickListener: (Int, DialogFragment) -> Unit
) : OffsetDialogFragment<LinearLayout>() {
    override val forceExpand: Boolean = true
    private val binding by viewBinding(DialogOptionPickerBinding::bind)
    private val viewModel: CryptoWalletsDialogViewModel by viewModels()
    private var itemList = listOf<IconifiedViewItem>()
    private var didFocusOnSelected = false
    private val dashFormat = MonetaryFormat().withLocale(GenericUtils.getDeviceLocale())
        .noCode().minDecimals(6).optionalDecimals()
    private var currentExchangeRate: org.dash.wallet.common.data.ExchangeRate? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_option_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchTitle.text = getString(R.string.select_a_coin)

        val adapter = RadioGroupAdapter(0, true) { item, _ ->
            val index = itemList.indexOfFirst { it.title == item.title }
            clickListener.invoke(index, this)
        }
        val divider = ContextCompat.getDrawable(requireContext(), R.drawable.list_divider)!!
        val decorator = ListDividerDecorator(
            divider,
            showAfterLast = false,
            marginStart = resources.getDimensionPixelOffset(R.dimen.divider_margin_horizontal),
            marginEnd = resources.getDimensionPixelOffset(R.dimen.divider_margin_horizontal)
        )
        binding.contentList.addItemDecoration(decorator)
        binding.contentList.adapter = adapter

        binding.searchQuery.doOnTextChanged { text, _, _, _ ->
            binding.clearBtn.isVisible = !text.isNullOrEmpty()

            adapter.submitList(
                if (text.isNullOrBlank()) {
                    itemList
                } else {
                    filterByQuery(itemList, text.toString())
                }
            )
        }

        binding.searchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val inputManager = requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputManager.toggleSoftInput(0, 0)
            }

            true
        }

        binding.clearBtn.setOnClickListener {
            binding.searchQuery.text.clear()
        }

        viewModel.exchangeRate.observe(viewLifecycleOwner) { rates ->

            itemList = userAccountsWithBalance.map {
                val icon = getFlagFromCurrencyCode(it.coinBaseUserAccountData.currency?.code ?: "")
                val iconUrl =
                    if (icon == R.drawable.ic_default_flag && it.coinBaseUserAccountData.currency?.code.isNullOrEmpty()
                        .not()
                    ) {
                        "https://raw.githubusercontent.com/jsupa/crypto-icons/main/icons/${it.coinBaseUserAccountData.currency?.code?.lowercase()}.png"
                    } else {
                        null
                    }

                val cryptoCurrencyBalance =
                    if (it.coinBaseUserAccountData.balance?.amount.isNullOrEmpty() || it.coinBaseUserAccountData.balance?.amount?.toDouble() == 0.0) {
                        MonetaryFormat().withLocale(GenericUtils.getDeviceLocale())
                            .noCode().minDecimals(2).optionalDecimals().format(Coin.ZERO).toString()
                    } else {
                        it.coinBaseUserAccountData.balance?.amount
                    }

                if (rates != null) {
                    currentExchangeRate = rates
                }

                IconifiedViewItem(
                    it.coinBaseUserAccountData.currency?.code ?: "",
                    it.coinBaseUserAccountData.currency?.name ?: "",
                    icon,
                    IconSelectMode.None,
                    setLocalFaitAmount(it)?.first,
                    subtitleAdditionalInfo = cryptoCurrencyBalance,
                    iconUrl = iconUrl
                )
            }

            if (!didFocusOnSelected) {
                lifecycleScope.launch {
                    delay(250)
                    adapter.submitList(itemList)
                    val selectedRateIndex =
                        itemList.indexOfFirst { it.additionalInfo == selectedCurrencyCode }
                    adapter.selectedIndex = selectedRateIndex
                    binding.contentList.scrollToPosition(selectedRateIndex)
                    didFocusOnSelected = true
                }
            } else {
                val list = if (binding.searchQuery.text.isNullOrBlank()) {
                    itemList
                } else {
                    filterByQuery(itemList, binding.searchQuery.text.toString())
                }
                val layoutManager = binding.contentList.layoutManager as LinearLayoutManager
                val scrollPosition = layoutManager.findFirstVisibleItemPosition()
                adapter.submitList(list)
                binding.contentList.scrollToPosition(scrollPosition)
            }
        }
    }

    private fun setLocalFaitAmount(coinBaseUserAccountData: CoinBaseUserAccountDataUIModel): Pair<String, Coin>? {

        currentExchangeRate?.let {
            return coinBaseUserAccountData.getCoinBaseExchangeRateConversion(it)
        }

        return null
    }

    override fun dismiss() {
        lifecycleScope.launch {
            delay(300)
            super.dismiss()
        }
    }

    private fun filterByQuery(
        items: List<IconifiedViewItem>,
        query: String
    ): List<IconifiedViewItem> {
        return items.filter {
            it.title.lowercase().contains(query.lowercase()) ||
                it.additionalInfo?.lowercase()?.contains(query.lowercase()) == true
        }
    }

    private fun getFlagFromCurrencyCode(currencyCode: String): Int {
        val resourceId = resources.getIdentifier(
            "currency_code_" + currencyCode.lowercase(),
            "drawable", requireContext().packageName
        )
        return if (resourceId == 0) R.drawable.ic_default_flag else resourceId
    }
}
