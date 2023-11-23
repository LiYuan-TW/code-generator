
    drop table if exists holiday cascade;

    create table holiday (
        id bigserial not null,
        createdDate timestamp(6) with time zone,
        updatedDate timestamp(6) with time zone,
        day integer,
        month integer,
        year integer,
        primary key (id)
    );
