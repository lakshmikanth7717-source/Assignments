1\. Core Entities \& Attributes

A. Movie / Event

Attributes: movie\_id (PK), title, description, duration\_mins, language, genre, release\_date, created\_at



B. Venue \& Screen

Venue: venue\_id (PK), name, city, address, created\_at



Screen: screen\_id (PK), venue\_id (FK), name (e.g., "Screen 1", "IMAX"), total\_capacity, created\_at



C. Show (Performance Instance)

Represents a specific screening of a movie at a given screen and time.



Attributes: show\_id (PK), movie\_id (FK), screen\_id (FK), start\_time, end\_time, status (ACTIVE, CANCELLED, HOUSEFUL), created\_at



D. Seat (Master Layout)

Represents the physical layout of seats in a screen.



Attributes: seat\_id (PK), screen\_id (FK), row\_identifier (e.g., 'A', 'B'), seat\_number (e.g., 1, 2), seat\_category (e.g., NORMAL, VIP, EXECUTIVE), created\_at



E. Show Seat (Real-Time State per Show)

Crucial for concurrency: Manages the live state of every seat for a specific show.



Attributes: show\_id, seat\_id (Composite PK), status (AVAILABLE, LOCKED, BOOKED), locked\_by\_user\_id, lock\_expires\_at, version (for optimistic locking)



F. Booking / Order

Tracks the lifecycle of a user's ticket purchase attempt.



Attributes: booking\_id (PK), user\_id, show\_id (FK), status (PENDING, CONFIRMED, EXPIRED, CANCELLED), total\_amount, expires\_at, created\_at, updated\_at



G. Payment \& Webhook Ledger (Idempotency)

Tracks payment attempts and webhook payloads to prevent duplicate processing.



Attributes: payment\_id (PK), booking\_id (FK), gateway\_transaction\_id, idempotency\_key (UNIQUE), amount, status (INITIATED, SUCCESS, FAILED), gateway\_response\_payload, created\_at

