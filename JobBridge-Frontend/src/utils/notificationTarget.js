export function notificationTarget(notification, role) {
  const id = notification.relatedId;
  const company = role === 'COMPANY';
  switch (notification.notificationType) {
    case 'REVIEW_MODERATION':
      return role==='ADMIN'?'/admin/reports?view=reviews':null;
    case 'INTERVIEW_REVIEW':
      return role==='ADMIN'?'/admin/reports':company?'/company/reviews':'/interview-reviews';
    case 'ATTENDANCE':
      return role==='ADMIN'?'/admin/reports?view=requests':company?'/company/reviews?view=requests':'/interview-reviews?view=requests';
    case 'REPORT':
      return role==='ADMIN'?'/admin/reports?view=reports':company?'/company/reviews?view=reports':'/interview-reviews';
    case 'ADVERTISEMENT':
      return role === 'ADMIN' ? '/admin/advertising' : '/company/advertising';
    case 'APPLICATION':
    case 'INTERVIEW':
      if (role === 'ADMIN') return '/admin';
      return id
        ? `${company ? '/company' : ''}/applications/${id}`
        : company ? '/company/applicants' : '/applications';
    case 'COMPANY':
      return company ? '/company/profile' : '/companies';
    case 'JOB_REVIEW':
      return role === 'ADMIN' ? `/admin/jobs${id ? `?focus=${id}` : ''}` : null;
    case 'JOB_DELETED':
      return company ? '/company/jobs' : '/jobs';
    case 'JOB':
      return company ? (id ? `/company/jobs/${id}/edit` : '/company/jobs') : id ? `/jobs/${id}` : '/jobs';
    case 'INQUIRY':
      return role === 'ADMIN' ? `/admin/inquiries${id ? `?focus=${id}` : ''}` : '/inquiries?view=history';
    case 'EMPLOYMENT':
      return role === 'ADMIN' ? `/admin/employment${id ? `?focus=${id}` : ''}` : company ? `/company/employment${id ? `?focus=${id}` : ''}` : id ? `/employment/${id}` : '/employment';
    default:
      return null;
  }
}
