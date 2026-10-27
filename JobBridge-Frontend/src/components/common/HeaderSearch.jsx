import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function HeaderSearch({ onSubmitted }) {
  const [query, setQuery] = useState('');
  const navigate = useNavigate();
  return (
    <form className="header-search" role="search" aria-label="채용공고 빠른 검색" autoComplete="off" onSubmit={(event) => {
      event.preventDefault();
      const value = query.trim();
      onSubmitted?.();
      navigate(value ? `/jobs?q=${encodeURIComponent(value)}&page=0` : '/jobs');
    }}>
      <input type="search" aria-label="상단 채용공고 검색" placeholder="어떤 일을 찾으세요?" value={query} maxLength={100} onChange={(event) => setQuery(event.target.value)} />
      <button type="submit" aria-label="채용공고 검색하기"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" /></svg></button>
    </form>
  );
}
