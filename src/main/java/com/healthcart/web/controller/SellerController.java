package com.healthcart.web.controller;

import com.healthcart.domain.*;
import com.healthcart.repository.OrderItemRepository;
import com.healthcart.service.*;
import com.healthcart.web.dto.ProductForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/seller")
public class SellerController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final UserService userService;
    private final OrderItemRepository orderItemRepository;
    private final SvgChartService svgChartService;

    public SellerController(
            ProductService productService,
            InventoryService inventoryService,
            OrderService orderService,
            UserService userService,
            OrderItemRepository orderItemRepository,
            SvgChartService svgChartService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.userService = userService;
        this.orderItemRepository = orderItemRepository;
        this.svgChartService = svgChartService;
    }

    private User getAuthenticatedSeller(Authentication auth) {
        return userService.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Seller not found"));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User seller = getAuthenticatedSeller(auth);

        BigDecimal revenue = orderItemRepository.calculateSellerTotalRevenue(seller.getId());
        long unitsSold = orderItemRepository.countSellerTotalUnitsSold(seller.getId());
        long orderCount = orderItemRepository.countSellerTotalOrders(seller.getId());
        long lowStockCount = inventoryService.countLowStockProducts(seller.getId());

        model.addAttribute("revenue", revenue);
        model.addAttribute("unitsSold", unitsSold);
        model.addAttribute("orderCount", orderCount);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("lowStockProducts", inventoryService.getLowStockProducts(seller.getId()));

        return "seller/dashboard";
    }

    // --- PRODUCTS ---
    @GetMapping("/products")
    public String myProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Authentication auth) {

        User seller = getAuthenticatedSeller(auth);
        Page<Product> products = productService.getSellerProducts(seller.getId(), page, size);

        model.addAttribute("products", products);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        model.addAttribute("lowStockCount", inventoryService.countLowStockProducts(seller.getId()));
        return "seller/products";
    }

    @GetMapping("/products/new")
    public String newProductForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("categories", productService.getAllCategories());
        return "seller/product-form";
    }

    @PostMapping("/products")
    public String createProduct(
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Authentication auth,
            RedirectAttributes redirectAttributes,
            Model model) {

        User seller = getAuthenticatedSeller(auth);
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", productService.getAllCategories());
            return "seller/product-form";
        }

        try {
            productService.createProduct(seller, form.getCategoryId(), form.getName(), form.getDescription(),
                    form.getPrice(), form.getStock(), form.getLowStockThreshold(), form.isRxRequired(), form.getImage());
            redirectAttributes.addFlashAttribute("successMessage", "Product listed successfully!");
            return "redirect:/seller/products";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("categories", productService.getAllCategories());
            return "seller/product-form";
        }
    }

    @GetMapping("/products/{id}/edit")
    @PreAuthorize("@ownership.canEditProduct(#id, authentication)")
    public String editProductForm(@PathVariable Long id, Model model) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        ProductForm form = new ProductForm();
        form.setId(product.getId());
        form.setName(product.getName());
        form.setDescription(product.getDescription());
        form.setCategoryId(product.getCategory().getId());
        form.setPrice(product.getPrice());
        form.setStock(product.getStock());
        form.setLowStockThreshold(product.getLowStockThreshold());
        form.setRxRequired(product.isRxRequired());
        form.setActive(product.isActive());

        model.addAttribute("productForm", form);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("product", product);
        return "seller/product-form";
    }

    @PostMapping("/products/{id}")
    @PreAuthorize("@ownership.canEditProduct(#id, authentication)")
    public String updateProduct(
            @PathVariable Long id,
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Authentication auth,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", productService.getAllCategories());
            return "seller/product-form";
        }

        try {
            productService.updateProduct(id, form.getCategoryId(), form.getName(), form.getDescription(),
                    form.getPrice(), form.getStock(), form.getLowStockThreshold(), form.isRxRequired(),
                    form.isActive(), form.getImage(), auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully.");
            return "redirect:/seller/products";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("categories", productService.getAllCategories());
            return "seller/product-form";
        }
    }

    @PostMapping("/products/{id}/delete")
    @PreAuthorize("@ownership.canEditProduct(#id, authentication)")
    public String deleteProduct(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            productService.deleteProduct(id, auth.getName());
            redirectAttributes.addFlashAttribute("infoMessage", "Product removed from catalog.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/seller/products";
    }

    // --- INVENTORY ---
    @GetMapping("/inventory")
    public String inventory(Model model, Authentication auth) {
        User seller = getAuthenticatedSeller(auth);
        Page<Product> products = productService.getSellerProducts(seller.getId(), 0, 50);

        model.addAttribute("products", products.getContent());
        model.addAttribute("lowStockCount", inventoryService.countLowStockProducts(seller.getId()));
        return "seller/inventory";
    }

    @PostMapping("/inventory/{id}")
    @PreAuthorize("@ownership.canEditProduct(#id, authentication)")
    public String updateInventoryStock(
            @PathVariable Long id,
            @RequestParam int stock,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            inventoryService.updateStock(id, stock, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Stock updated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/seller/inventory";
    }

    // --- ORDERS ---
    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Authentication auth) {

        User seller = getAuthenticatedSeller(auth);
        Page<OrderItem> items = orderService.getSellerOrderItems(seller.getId(), status, page, size);

        model.addAttribute("items", items);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", items.getTotalPages());
        return "seller/orders";
    }

    @PostMapping("/orders/items/{orderItemId}/status")
    @PreAuthorize("@ownership.canManageOrderItem(#orderItemId, authentication)")
    public String updateItemStatus(
            @PathVariable Long orderItemId,
            @RequestParam OrderStatus status,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            orderService.updateItemFulfillmentStatus(orderItemId, status, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Fulfillment status updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/seller/orders";
    }

    // --- SALES REPORTS & JAVA SVG CHARTS ---
    @GetMapping("/reports")
    public String salesReports(Model model, Authentication auth) {
        User seller = getAuthenticatedSeller(auth);

        BigDecimal totalRevenue = orderItemRepository.calculateSellerTotalRevenue(seller.getId());
        long totalUnits = orderItemRepository.countSellerTotalUnitsSold(seller.getId());
        long totalOrders = orderItemRepository.countSellerTotalOrders(seller.getId());

        // Compute 7-day revenue trend in Java
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(6).withHour(0).withMinute(0);
        List<OrderItem> recentItems = orderItemRepository.findSellerItemsSince(seller.getId(), sevenDaysAgo);

        Map<String, BigDecimal> dailyRevenueMap = new LinkedHashMap<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("MMM dd");
        for (int i = 6; i >= 0; i--) {
            String dayKey = LocalDate.now().minusDays(i).format(dayFmt);
            dailyRevenueMap.put(dayKey, BigDecimal.ZERO);
        }

        for (OrderItem item : recentItems) {
            String dayKey = item.getOrder().getCreatedAt().toLocalDate().format(dayFmt);
            if (dailyRevenueMap.containsKey(dayKey)) {
                dailyRevenueMap.put(dayKey, dailyRevenueMap.get(dayKey).add(item.getSubtotal()));
            }
        }

        // Top 5 products
        List<Object[]> rawTop = orderItemRepository.findSellerTopProducts(seller.getId(), PageRequest.of(0, 5));
        List<SvgChartService.TopProductStat> topProducts = new ArrayList<>();
        for (Object[] row : rawTop) {
            String name = (String) row[0];
            long qty = ((Number) row[1]).longValue();
            BigDecimal rev = (BigDecimal) row[2];
            topProducts.add(new SvgChartService.TopProductStat(name, qty, rev));
        }

        // Pure Java server-side SVG generation (PRD 4.3 - no JavaScript charting)
        String trendSvg = svgChartService.renderRevenueTrendChart(dailyRevenueMap, 700, 260);
        String topProductsSvg = svgChartService.renderTopProductsChart(topProducts, 700, 220);

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalUnits", totalUnits);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("trendSvg", trendSvg);
        model.addAttribute("topProductsSvg", topProductsSvg);
        model.addAttribute("topProductsList", topProducts);

        return "seller/reports";
    }
}
