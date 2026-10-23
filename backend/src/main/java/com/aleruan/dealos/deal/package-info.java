/**
 * Deals: what Aleruan sells to a customer, what each line costs at the subcontractor or supplier,
 * and the resulting margin. Margin is always computed from the lines, never typed in.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Deal",
        allowedDependencies = {"platform::audit", "platform::web", "platform::security", "counterparty"})
package com.aleruan.dealos.deal;
