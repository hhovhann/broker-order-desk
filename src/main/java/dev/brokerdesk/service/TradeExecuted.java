package dev.brokerdesk.service;

public record TradeExecuted(String symbol, long priceMills, long quantity) {}
