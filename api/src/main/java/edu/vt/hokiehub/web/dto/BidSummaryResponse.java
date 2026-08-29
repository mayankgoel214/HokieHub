package edu.vt.hokiehub.web.dto;

import java.math.BigDecimal;

/**
 * How much interest a listing has attracted, without saying who from.
 *
 * Anyone can see that a bike has four offers and that the best is $180; only the
 * seller can see that one of them is from a particular person, and only bidders
 * see their own. Publishing the names would turn a marketplace into a list of who
 * wants what.
 */
public record BidSummaryResponse(long count, BigDecimal highest) {

    public static final BidSummaryResponse NONE = new BidSummaryResponse(0, null);
}
