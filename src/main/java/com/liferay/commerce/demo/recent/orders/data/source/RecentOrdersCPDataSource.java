package com.liferay.commerce.demo.recent.orders.data.source;

import com.liferay.commerce.account.model.CommerceAccount;
import com.liferay.commerce.constants.CommerceOrderConstants;
import com.liferay.commerce.constants.CommerceWebKeys;
import com.liferay.commerce.context.CommerceContext;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderItem;
import com.liferay.commerce.product.catalog.CPCatalogEntry;
import com.liferay.commerce.product.data.source.CPDataSource;
import com.liferay.commerce.product.data.source.CPDataSourceResult;
import com.liferay.commerce.product.model.CPDefinition;
import com.liferay.commerce.product.util.CPDefinitionHelper;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.WebKeys;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author Jeff Handa
 */
@Component(
        immediate = true,
        property = "commerce.product.data.source.name=" + RecentOrdersCPDataSource.NAME,
        service = CPDataSource.class
)
public class RecentOrdersCPDataSource implements CPDataSource {

    public static final String NAME = "recent-orders-data-source";

    @Override
    public String getLabel(Locale locale) {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
                "content.Language", locale, getClass());

        return LanguageUtil.get(resourceBundle, "recent-orders-data-source");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CPDataSourceResult getResult(HttpServletRequest httpServletRequest, int start, int end) throws Exception {

        List<CPCatalogEntry> cpCatalogEntries = new ArrayList<>();

        ThemeDisplay themeDisplay = (ThemeDisplay)httpServletRequest.getAttribute(WebKeys.THEME_DISPLAY);
        long groupId = themeDisplay.getScopeGroupId();
        Locale locale = themeDisplay.getLocale();

        CommerceContext commerceContext = (CommerceContext)httpServletRequest.getAttribute(CommerceWebKeys.COMMERCE_CONTEXT);
        CommerceAccount commerceAccount = commerceContext.getCommerceAccount();
        long commerceAccountId = commerceAccount.getCommerceAccountId();

        _log.debug("Fetching orders for " + commerceAccountId);

        List<CommerceOrder> commerceOrders =  _commerceOrderLocalService.getCommerceOrdersByCommerceAccountId(
                commerceAccount.getCommerceAccountId(), QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);

        _log.debug("Found " + commerceOrders.size() + " orders for " + commerceAccountId);

        for (CommerceOrder commerceOrder : commerceOrders){
            if (commerceOrder.getOrderStatus() == CommerceOrderConstants.ORDER_STATUS_COMPLETED){

                _log.debug("Found  a completed order " + commerceOrder.getCommerceOrderId());

                for (CommerceOrderItem commerceOrderItem : commerceOrder.getCommerceOrderItems()){

                    CPDefinition cpDefinition = commerceOrderItem.getCPDefinition();

                    _log.debug("Found  an item " + cpDefinition.getName());

                    boolean entryExists = false;

                    try{

                        entryExists = cpCatalogEntries
                                .stream().map(CPCatalogEntry::getCPDefinitionId)
                                .anyMatch(Long.valueOf(cpDefinition.getCPDefinitionId())::equals);

                    }catch (Exception e){
                        _log.error(e.getCause());
                    }

                    if (!entryExists){

                        _log.debug(cpDefinition.getName() + " doesn't exist, adding");

                        CPCatalogEntry currentCPCatalogEntry = _cpDefinitionHelper.getCPCatalogEntry(
                                commerceAccountId, groupId, cpDefinition.getCPDefinitionId(), locale);

                        cpCatalogEntries.add(currentCPCatalogEntry);

                        _log.debug("Now we have " + cpCatalogEntries.size() + " items");
                    }
                }
            }
        }

        if (cpCatalogEntries.size() == 0){
            return new CPDataSourceResult(new ArrayList<>(), 0);
        }

        if (end >= cpCatalogEntries.size()){
            end = cpCatalogEntries.size();
        }

        _log.debug("Found " + cpCatalogEntries.size() + " items for " + commerceAccountId);

        return new CPDataSourceResult(cpCatalogEntries.subList(start, end), cpCatalogEntries.size());
    }

    private static final Log _log = LogFactoryUtil.getLog(
            RecentOrdersCPDataSource.class);

    @Reference
    private CommerceOrderLocalService _commerceOrderLocalService;

    @Reference
    private CPDefinitionHelper _cpDefinitionHelper;
}