package com.healthcart.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class SvgChartService {

    public record TopProductStat(String name, long quantitySold, BigDecimal revenue) {}

    /**
     * Generates a modern, clean responsive SVG bar/trend chart server-side in Java.
     */
    public String renderRevenueTrendChart(Map<String, BigDecimal> dailyData, int width, int height) {
        if (dailyData == null || dailyData.isEmpty()) {
            return "<svg viewBox='0 0 " + width + " " + height + "' class='w-100' style='max-height: 260px;' xmlns='http://www.w3.org/2000/svg'>" +
                   "<text x='50%' y='50%' dominant-baseline='middle' text-anchor='middle' fill='#6c757d' font-size='14' font-family='sans-serif'>No sales recorded in this period</text></svg>";
        }

        BigDecimal maxVal = dailyData.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (maxVal.compareTo(BigDecimal.ZERO) == 0) {
            maxVal = BigDecimal.valueOf(100);
        }

        int paddingLeft = 55;
        int paddingRight = 25;
        int paddingTop = 30;
        int paddingBottom = 45;

        int plotWidth = width - paddingLeft - paddingRight;
        int plotHeight = height - paddingTop - paddingBottom;

        int numBars = dailyData.size();
        double barWidth = (double) plotWidth / numBars * 0.65;
        double slotWidth = (double) plotWidth / numBars;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<svg viewBox='0 0 %d %d' class='w-100' style='max-height: 280px;' xmlns='http://www.w3.org/2000/svg' role='img' aria-label='Sales Trend Chart'>", width, height));
        
        // Defs for gradients
        sb.append("<defs>")
          .append("<linearGradient id='barGrad' x1='0' y1='0' x2='0' y2='1'>")
          .append("<stop offset='0%' stop-color='#0d9488'/>")
          .append("<stop offset='100%' stop-color='#14b8a6'/>")
          .append("</linearGradient>")
          .append("</defs>");

        // Grid lines (horizontal)
        int gridSteps = 4;
        for (int i = 0; i <= gridSteps; i++) {
            double y = paddingTop + (double) i / gridSteps * plotHeight;
            BigDecimal labelVal = maxVal.multiply(BigDecimal.valueOf(gridSteps - i)).divide(BigDecimal.valueOf(gridSteps), 0, java.math.RoundingMode.HALF_UP);
            sb.append(String.format("<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke='#e2e8f0' stroke-width='1' stroke-dasharray='3,3'/>",
                    paddingLeft, y, width - paddingRight, y));
            sb.append(String.format("<text x='%d' y='%.1f' text-anchor='end' dominant-baseline='middle' fill='#64748b' font-size='11' font-family='sans-serif'>₹%s</text>",
                    paddingLeft - 8, y, labelVal.toString()));
        }

        // Draw Bars
        int index = 0;
        for (Map.Entry<String, BigDecimal> entry : dailyData.entrySet()) {
            double x = paddingLeft + (index * slotWidth) + (slotWidth - barWidth) / 2.0;
            double barHeight = entry.getValue().doubleValue() / maxVal.doubleValue() * plotHeight;
            double y = paddingTop + (plotHeight - barHeight);

            // Bar with subtle rounded top
            sb.append(String.format("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' rx='4' fill='url(#barGrad)' opacity='0.9'>",
                    x, y, barWidth, barHeight));
            sb.append(String.format("<title>%s: ₹%s</title></rect>", entry.getKey(), entry.getValue().toPlainString()));

            // Date label below bar
            sb.append(String.format("<text x='%.1f' y='%d' text-anchor='middle' fill='#64748b' font-size='10' font-family='sans-serif'>%s</text>",
                    x + barWidth / 2.0, height - 15, entry.getKey()));

            index++;
        }

        sb.append("</svg>");
        return sb.toString();
    }

    /**
     * Generates a horizontal bar chart of top selling products.
     */
    public String renderTopProductsChart(List<TopProductStat> topProducts, int width, int height) {
        if (topProducts == null || topProducts.isEmpty()) {
            return "<svg viewBox='0 0 " + width + " " + height + "' class='w-100' style='max-height: 220px;' xmlns='http://www.w3.org/2000/svg'>" +
                   "<text x='50%' y='50%' dominant-baseline='middle' text-anchor='middle' fill='#6c757d' font-size='14' font-family='sans-serif'>No product sales data</text></svg>";
        }

        long maxQty = topProducts.stream().mapToLong(TopProductStat::quantitySold).max().orElse(1);
        if (maxQty == 0) maxQty = 1;

        int rowHeight = 42;
        int calculatedHeight = Math.max(height, topProducts.size() * rowHeight + 30);
        int labelWidth = 160;
        int barMaxWidth = width - labelWidth - 90;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<svg viewBox='0 0 %d %d' class='w-100' style='max-height: %dpx;' xmlns='http://www.w3.org/2000/svg' role='img' aria-label='Top Products Chart'>",
                width, calculatedHeight, calculatedHeight));

        int y = 25;
        for (TopProductStat stat : topProducts) {
            String displayName = stat.name();
            if (displayName.length() > 22) {
                displayName = displayName.substring(0, 19) + "...";
            }

            double barW = ((double) stat.quantitySold() / maxQty) * barMaxWidth;
            if (barW < 6) barW = 6;

            // Product name label
            sb.append(String.format("<text x='%d' y='%d' text-anchor='end' dominant-baseline='middle' fill='#334155' font-size='12' font-family='sans-serif' font-weight='500'>%s</text>",
                    labelWidth - 10, y + 10, escapeXml(displayName)));

            // Horizontal bar
            sb.append(String.format("<rect x='%d' y='%d' width='%.1f' height='20' rx='4' fill='#0284c7'>",
                    labelWidth, y, barW));
            sb.append(String.format("<title>%s: %d units (₹%s)</title></rect>",
                    escapeXml(stat.name()), stat.quantitySold(), stat.revenue().toPlainString()));

            // Value label
            sb.append(String.format("<text x='%.1f' y='%d' dominant-baseline='middle' fill='#0f172a' font-size='11' font-family='sans-serif' font-weight='bold'> %d units</text>",
                    labelWidth + barW + 8, y + 10, stat.quantitySold()));

            y += rowHeight;
        }

        sb.append("</svg>");
        return sb.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
