package com.ailabs.payments.service;

import java.util.List;

public record PaymentPage(List<PaymentView> items, int page, int size, long totalItems) {
}
