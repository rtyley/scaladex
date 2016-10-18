create table scaladex.Scala_Dependencies(
  release1_id int not null,
  foreign key(release1_id) references scaladex.Releases(id),
  release2_id int not null,
  foreign key(release2_id) references scaladex.Releases(id),
  primary key(release1_id, release2_id),
  scope varchar(255)
);

create table scaladex.Java_Dependencies(
  release_id integer not null,
  foreign key(release_id) references scaladex.Releases(id),

  java_release_id integer not null,
  foreign key(java_release_id) references scaladex.Java_Releases(id),

  primary key(release_id, java_release_id),
  scope varchar(255)
);

create table scaladex.Java_Releases(
  id integer not null primary key,
  groupId varchar(1024) not null,
  artifactId varchar(1024) not null,
  version varchar(1024) not null
)