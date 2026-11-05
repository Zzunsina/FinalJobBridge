export function ordersForAdminTab(orders, tab) {
  if (tab === 'review')
    return orders.filter(
      (o) =>
        ['PENDING_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'PAUSED', 'JOB_CLOSED', 'JOB_STOPPED'].includes(o.reviewStatus) &&
        !['CANCELLED', 'PARTIAL_CANCELLED'].includes(o.paymentStatus) &&
        o.refundStatus === 'NONE',
    );
  if (tab === 'refund')
    return orders.filter((o) =>
      ['PAID', 'CANCELLED', 'PARTIAL_CANCELLED'].includes(o.paymentStatus),
    );
  return [];
}
