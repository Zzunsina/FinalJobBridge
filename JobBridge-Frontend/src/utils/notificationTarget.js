export function notificationTarget(notification, role) {
  const id = notification.relatedId;
  const company = role === 'COMPANY';
  switch (notification.notificationType) {
    case 'APPLICATION':
    case 'INTERVIEW':
      if (role === 'ADMIN') return '/admin';
      return id
        ? `${company ? '/company' : ''}/applications/${id}`
        : company ? '/company/applicants' : '/applications';
    case 'COMPANY':
      return company ? '/company/profile' : '/companies';
    case 'JOB':
      return company ? (id ? `/company/jobs/${id}/edit` : '/company/jobs') : id ? `/jobs/${id}` : '/jobs';
    case 'INQUIRY':
      return role === 'ADMIN' ? `/admin/inquiries${id ? `?focus=${id}` : ''}` : '/inquiries?view=history';
    case 'EMPLOYMENT':
      return role === 'ADMIN' ? `/admin/employment${id ? `?focus=${id}` : ''}` : company ? `/company/employment${id ? `/${id}` : ''}` : id ? `/employment/${id}` : '/employment';
    default:
      return null;
  }
}
